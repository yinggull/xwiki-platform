/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.rest.resources.attachments;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

import javax.mail.BodyPart;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Response.Status;

import org.xwiki.component.annotation.Component;
import org.xwiki.rest.DomainObjectFactory;
import org.xwiki.rest.Utils;
import org.xwiki.rest.model.jaxb.Attachment;
import org.xwiki.rest.model.jaxb.Attachments;
import org.xwiki.rest.resources.BaseAttachmentsResource;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * @version $Id$
 */
@Component("org.xwiki.rest.resources.attachments.AttachmentsResource")
@Path("/wikis/{wikiName}/spaces/{spaceName}/pages/{pageName}/attachments")
public class AttachmentsResource extends BaseAttachmentsResource
{
    private static String FORM_FILENAME_FIELD = "filename";

    @GET
    public Attachments getAttachments(@PathParam("wikiName") String wikiName, @PathParam("spaceName") String spaceName,
        @PathParam("pageName") String pageName, @QueryParam("start") @DefaultValue("0") Integer start,
        @QueryParam("number") @DefaultValue("-1") Integer number) throws XWikiException
    {
        DocumentInfo documentInfo = getDocumentInfo(wikiName, spaceName, pageName, null, null, true, false);
        Document doc = documentInfo.getDocument();

        return getAttachmentsForDocument(doc, start, number);
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response addAttachment(@PathParam("wikiName") String wikiName, @PathParam("spaceName") String spaceName,
        @PathParam("pageName") String pageName, Multipart multipart) throws MessagingException, IOException,
        XWikiException
    {
        DocumentInfo documentInfo = getDocumentInfo(wikiName, spaceName, pageName, null, null, true, true);

        Document doc = documentInfo.getDocument();

        if (!doc.hasAccessLevel("edit", Utils.getXWikiUser(componentManager))) {
            throw new WebApplicationException(Status.UNAUTHORIZED);
        }

        /* The name to be used */
        String attachmentName = null;

        /* The actual filename of the sent file */
        String actualFileName = null;

        /* The specified file name using a form field */
        String overriddenFileName = null;
        String contentType = null;
        InputStream inputStream = null;

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);

            /* Get the content disposition headers */
            Enumeration e = bodyPart.getMatchingHeaders(new String[] {"Content-disposition"});
            while (e.hasMoreElements()) {
                Header h = (Header) e.nextElement();

                /* Parse header data. Normally headers are in the form form-data; key="value"; ... */
                if (h.getValue().startsWith("form-data")) {
                    String[] fieldData = h.getValue().split(";");
                    for (String s : fieldData) {
                        String[] pair = s.split("=");
                        if (pair.length == 2) {
                            String key = pair[0].trim();
                            String value = pair[1].replace("\"", "").trim();

                            if ("name".equals(key)) {
                                if (FORM_FILENAME_FIELD.equals(value)) {
                                    overriddenFileName = bodyPart.getContent().toString();
                                }
                            } else if ("filename".equals(key)) {
                                actualFileName = value;
                                contentType = bodyPart.getContentType();
                                inputStream = bodyPart.getInputStream();
                            }
                        }
                    }
                }
            }
        }

        if (overriddenFileName != null) {
            attachmentName = overriddenFileName;
        } else {
            attachmentName = actualFileName;
        }

        if (attachmentName == null) {
            throw new WebApplicationException(Status.BAD_REQUEST);
        }

        /* Clear the fileName */
        attachmentName =
            Utils.getXWikiContext(componentManager).getWiki().clearName(attachmentName, false, true,
                Utils.getXWikiContext(componentManager));

        byte[] buffer = new byte[4096];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            int read = inputStream.read(buffer);
            if (read != 4096) {
                if (read != -1) {
                    baos.write(buffer, 0, read);
                }

                break;
            } else {
                baos.write(buffer);
            }
        }
        baos.flush();

        /* Attach the file */
        boolean existed = false;

        XWikiDocument xwikiDocument =
            Utils.getXWiki(componentManager).getDocument(doc.getPrefixedFullName(),
                Utils.getXWikiContext(componentManager));
        XWikiAttachment xwikiAttachment = xwikiDocument.getAttachment(attachmentName);
        if (xwikiAttachment == null) {
            xwikiAttachment = new XWikiAttachment();
            xwikiDocument.getAttachmentList().add(xwikiAttachment);
        } else {
            existed = true;
        }

        xwikiAttachment.setContent(baos.toByteArray());
        xwikiAttachment.setAuthor(Utils.getXWikiUser(componentManager));
        xwikiAttachment.setFilename(attachmentName);
        xwikiAttachment.setDoc(xwikiDocument);

        xwikiDocument.saveAttachmentContent(xwikiAttachment, Utils.getXWikiContext(componentManager));

        doc.save();

        URL url =
            Utils.getXWikiContext(componentManager).getURLFactory().createAttachmentURL(attachmentName, spaceName,
                doc.getName(), "download", null, wikiName, Utils.getXWikiContext(componentManager));
        String attachmentXWikiAbsoluteUrl = url.toString();
        String attachmentXWikiRelativeUrl =
            Utils.getXWikiContext(componentManager).getURLFactory()
                .getURL(url, Utils.getXWikiContext(componentManager));

        Attachment attachment =
            DomainObjectFactory.createAttachment(objectFactory, uriInfo.getBaseUri(), new com.xpn.xwiki.api.Attachment(
                doc, xwikiAttachment, Utils.getXWikiContext(componentManager)), attachmentXWikiRelativeUrl,
                attachmentXWikiAbsoluteUrl);

        if (existed) {
            return Response.status(Status.ACCEPTED).entity(attachment).build();
        } else {
            return Response.created(
                UriBuilder.fromUri(uriInfo.getBaseUri()).path(AttachmentResource.class).build(wikiName, spaceName,
                    pageName, attachmentName)).entity(attachment).build();
        }
    }

}
