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
package org.xwiki.rendering.macro.script;

import java.util.Collections;

import junit.framework.Test;
import junit.framework.TestCase;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.xwiki.component.descriptor.DefaultComponentDescriptor;
import org.xwiki.observation.EventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.script.event.ScriptEvaluatingEvent;
import org.xwiki.rendering.scaffolding.RenderingTestSuite;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.test.ComponentManagerTestSetup;

/**
 * All Rendering integration tests defined in text files using a special format.
 * 
 * @version $Id$
 * @since 2.0M1
 */
public class RenderingTests extends TestCase
{
    public static Test suite() throws Exception
    {
        RenderingTestSuite suite = new RenderingTestSuite("Test all Parsers/Renderers");

        ComponentManagerTestSetup testSetup = new ComponentManagerTestSetup(suite);
        Mockery mockery = new Mockery();
        new ScriptMockSetup(mockery, testSetup.getComponentManager());

        // fake nested script validator never fails
        final EventListener nestedValidator
            = mockery.mock(EventListener.class, "nestedscriptmacrovalidator");
        mockery.checking(new Expectations() {{
            atLeast(1).of(nestedValidator).onEvent(with(any(Event.class)), with(any(MacroTransformationContext.class)),
                with(any(ScriptMacroParameters.class)));
            allowing(nestedValidator).getName();
                will(returnValue("nestedscriptmacrovalidator"));
            allowing(nestedValidator).getEvents();
                will(returnValue(Collections.singletonList((Event) new ScriptEvaluatingEvent())));
        }});
        DefaultComponentDescriptor<EventListener> validatorDescriptor
            = new DefaultComponentDescriptor<EventListener>();
        validatorDescriptor.setRole(EventListener.class);
        validatorDescriptor.setRoleHint("nestedscriptmacrovalidator");
        testSetup.getComponentManager().registerComponent(validatorDescriptor, nestedValidator);

        return testSetup;
    }
}