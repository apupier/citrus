/*
 * Copyright 2006-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.actions.dsl;

import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.consol.citrus.DefaultTestCaseRunner;
import com.consol.citrus.TestCase;
import com.consol.citrus.actions.ReceiveMessageAction;
import com.consol.citrus.container.SequenceAfterTest;
import com.consol.citrus.container.SequenceBeforeTest;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.dictionary.SimpleMappingDictionary;
import com.consol.citrus.endpoint.Endpoint;
import com.consol.citrus.endpoint.EndpointConfiguration;
import com.consol.citrus.message.DefaultMessage;
import com.consol.citrus.message.Message;
import com.consol.citrus.message.MessageType;
import com.consol.citrus.messaging.Consumer;
import com.consol.citrus.messaging.SelectiveConsumer;
import com.consol.citrus.report.TestActionListeners;
import com.consol.citrus.spi.ReferenceResolver;
import com.consol.citrus.testng.AbstractTestNGUnitTest;
import com.consol.citrus.validation.MessageValidator;
import com.consol.citrus.validation.TextEqualsMessageValidator;
import com.consol.citrus.validation.builder.PayloadTemplateMessageBuilder;
import com.consol.citrus.validation.builder.StaticMessageContentBuilder;
import com.consol.citrus.validation.callback.AbstractValidationCallback;
import com.consol.citrus.validation.context.HeaderValidationContext;
import com.consol.citrus.validation.json.JsonMessageValidationContext;
import com.consol.citrus.validation.xml.XmlMessageValidationContext;
import com.consol.citrus.variable.MessageHeaderVariableExtractor;
import com.consol.citrus.variable.VariableExtractor;
import com.consol.citrus.variable.dictionary.DataDictionary;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.oxm.Marshaller;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.consol.citrus.actions.ReceiveMessageAction.Builder.receive;
import static com.consol.citrus.validation.xml.XmlMessageValidationContext.Builder.xml;
import static com.consol.citrus.variable.MessageHeaderVariableExtractor.Builder.headerValueExtractor;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Christoph Deppisch
 */
public class ReceiveMessageActionBuilderTest extends AbstractTestNGUnitTest {

    private Endpoint messageEndpoint = Mockito.mock(Endpoint.class);
    private Consumer messageConsumer = Mockito.mock(Consumer.class);
    private EndpointConfiguration configuration = Mockito.mock(EndpointConfiguration.class);
    private Resource resource = Mockito.mock(Resource.class);
    private ReferenceResolver referenceResolver = Mockito.mock(ReferenceResolver.class);

    private Marshaller marshaller = Mockito.mock(Marshaller.class);
    private Object payloadModel = new Object();

    @BeforeClass
    public void prepareMarshaller() throws IOException {
        reset(marshaller);
        when(marshaller.supports(any(Class.class))).thenReturn(true);
        doAnswer(invocationOnMock -> {
            Result result = invocationOnMock.getArgument(1);
            if (result instanceof StreamResult) {
                ((StreamResult) result).getWriter().write("<TestRequest><Message>Hello Citrus!</Message></TestRequest>");
            }
            return null;
        }).when(marshaller).marshal(eq(payloadModel), any(Result.class));
    }

    @BeforeMethod
    public void prepareTestContext() {
        context.getMessageValidatorRegistry().addMessageValidator("default", new TextEqualsMessageValidator());
    }

    @Test
    public void testReceiveEmpty() {
        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(new DefaultMessage(""));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getValidationContexts().size(), 3);
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(HeaderValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(XmlMessageValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(JsonMessageValidationContext.class::isInstance));
    }

    @Test
    public void testReceiveBuilder() {
        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(new DefaultMessage("Foo").setHeader("operation", "foo"));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .messageType(MessageType.PLAINTEXT)
                        .message(new DefaultMessage("Foo").setHeader("operation", "foo")));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.PLAINTEXT.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getValidationContexts().size(), 3);
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(HeaderValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(XmlMessageValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(JsonMessageValidationContext.class::isInstance));

        Assert.assertTrue(action.getMessageBuilder() instanceof StaticMessageContentBuilder);
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getMessage().getPayload(), "Foo");
        Assert.assertNotNull(((StaticMessageContentBuilder)action.getMessageBuilder()).getMessage().getHeader("operation"));
    }

    @Test
    public void testReceiveBuilderWithPayloadModel() {
        reset(referenceResolver, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello Citrus!</Message></TestRequest>")
                        .setHeader("operation", "foo"));

        when(referenceResolver.resolve(TestContext.class)).thenReturn(context);
        when(referenceResolver.resolve(TestActionListeners.class)).thenReturn(new TestActionListeners());
        when(referenceResolver.resolveAll(SequenceBeforeTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.resolveAll(SequenceAfterTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.resolveAll(Marshaller.class)).thenReturn(Collections.<String, Marshaller>singletonMap("marshaller", marshaller));
        when(referenceResolver.resolve(Marshaller.class)).thenReturn(marshaller);

        context.setReferenceResolver(referenceResolver);
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payloadModel(payloadModel));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getValidationContexts().size(), 3);
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(HeaderValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(XmlMessageValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(JsonMessageValidationContext.class::isInstance));

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(),
                "<TestRequest><Message>Hello Citrus!</Message></TestRequest>");
    }

    @Test
    public void testReceiveBuilderWithPayloadModelExplicitMarshaller() {
        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello Citrus!</Message></TestRequest>")
                        .setHeader("operation", "foo"));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payload(payloadModel, marshaller));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getValidationContexts().size(), 3);
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(HeaderValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(XmlMessageValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(JsonMessageValidationContext.class::isInstance));

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "<TestRequest><Message>Hello Citrus!</Message></TestRequest>");

    }

    @Test
    public void testReceiveBuilderWithPayloadModelExplicitMarshallerName() {
        reset(referenceResolver, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello Citrus!</Message></TestRequest>")
                        .setHeader("operation", "foo"));

        when(referenceResolver.resolve(TestContext.class)).thenReturn(context);
        when(referenceResolver.resolve(TestActionListeners.class)).thenReturn(new TestActionListeners());
        when(referenceResolver.resolveAll(SequenceBeforeTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.resolveAll(SequenceAfterTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.isResolvable("myMarshaller")).thenReturn(true);
        when(referenceResolver.resolve("myMarshaller")).thenReturn(marshaller);

        context.setReferenceResolver(referenceResolver);
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payload(payloadModel, "myMarshaller"));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getValidationContexts().size(), 3);
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(HeaderValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(XmlMessageValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(JsonMessageValidationContext.class::isInstance));

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "<TestRequest><Message>Hello Citrus!</Message></TestRequest>");

    }

    @Test
    public void testReceiveBuilderWithPayloadString() {
        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("operation", "foo"));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payload("<TestRequest><Message>Hello World!</Message></TestRequest>"));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getValidationContexts().size(), 3);
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(HeaderValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(XmlMessageValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(JsonMessageValidationContext.class::isInstance));

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");

    }

    @Test
    public void testReceiveBuilderWithPayloadResource() throws IOException {
        reset(resource, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("operation", "foo"));

        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("<TestRequest><Message>Hello World!</Message></TestRequest>".getBytes()));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payload(resource));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getValidationContexts().size(), 3);
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(HeaderValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(XmlMessageValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(JsonMessageValidationContext.class::isInstance));

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");

    }

    @Test
    public void testReceiveBuilderWithEndpointName() {
        reset(referenceResolver, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("operation", "foo"));

        when(referenceResolver.resolve(TestContext.class)).thenReturn(context);
        when(referenceResolver.resolve("fooMessageEndpoint", Endpoint.class)).thenReturn(messageEndpoint);
        when(referenceResolver.resolve(TestActionListeners.class)).thenReturn(new TestActionListeners());
        when(referenceResolver.resolveAll(SequenceBeforeTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.resolveAll(SequenceAfterTest.class)).thenReturn(new HashMap<>());

        context.setReferenceResolver(referenceResolver);
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive("fooMessageEndpoint")
                        .payload("<TestRequest><Message>Hello World!</Message></TestRequest>"));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");
        Assert.assertEquals(action.getEndpointUri(), "fooMessageEndpoint");
        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());

    }

    @Test
    public void testReceiveBuilderWithTimeout() {
        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("operation", "foo"));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .timeout(1000L));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getReceiveTimeout(), 1000L);

    }

    @Test
    public void testReceiveBuilderWithHeaders() {
        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("some", "value")
                        .setHeader("operation", "sayHello")
                        .setHeader("foo", "bar"));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .headers(Collections.singletonMap("some", "value"))
                        .header("operation", "sayHello")
                        .header("foo", "bar"));

        runner.run(receive(messageEndpoint)
                        .header("operation", "sayHello")
                        .header("foo", "bar")
                        .headers(Collections.singletonMap("some", "value"))
                        .payload("<TestRequest><Message>Hello World!</Message></TestRequest>"));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 2);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);
        Assert.assertEquals(test.getActions().get(1).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertTrue(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getMessageHeaders().containsKey("some"));
        Assert.assertTrue(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getMessageHeaders().containsKey("operation"));
        Assert.assertTrue(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getMessageHeaders().containsKey("foo"));

        action = ((ReceiveMessageAction)test.getActions().get(1));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertTrue(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getMessageHeaders().containsKey("some"));
        Assert.assertTrue(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getMessageHeaders().containsKey("operation"));
        Assert.assertTrue(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getMessageHeaders().containsKey("foo"));

    }

    @Test
    public void testReceiveBuilderWithHeaderData() {
        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("operation", "foo")
                        .addHeaderData("<Header><Name>operation</Name><Value>foo</Value></Header>"));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .header("<Header><Name>operation</Name><Value>foo</Value></Header>"));

        runner.run(receive(messageEndpoint)
                        .message(new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>"))
                        .header("<Header><Name>operation</Name><Value>foo</Value></Header>"));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 2);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);
        Assert.assertEquals(test.getActions().get(1).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().size(), 1L);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().get(0), "<Header><Name>operation</Name><Value>foo</Value></Header>");
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderResources().size(), 0L);

        action = ((ReceiveMessageAction) test.getActions().get(1));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());

        Assert.assertTrue(action.getMessageBuilder() instanceof StaticMessageContentBuilder);
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getMessage().getPayload(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderData().size(), 1L);
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderData().get(0), "<Header><Name>operation</Name><Value>foo</Value></Header>");
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderResources().size(), 0L);

    }

    @Test
    public void testReceiveBuilderWithMultipleHeaderData() {
        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("operation", "foo")
                        .addHeaderData("<Header><Name>operation</Name><Value>foo1</Value></Header>")
                        .addHeaderData("<Header><Name>operation</Name><Value>foo2</Value></Header>"));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .header("<Header><Name>operation</Name><Value>foo1</Value></Header>")
                        .header("<Header><Name>operation</Name><Value>foo2</Value></Header>"));

        runner.run(receive(messageEndpoint)
                        .message(new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>"))
                        .header("<Header><Name>operation</Name><Value>foo1</Value></Header>")
                        .header("<Header><Name>operation</Name><Value>foo2</Value></Header>"));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 2);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);
        Assert.assertEquals(test.getActions().get(1).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().size(), 2L);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().get(0), "<Header><Name>operation</Name><Value>foo1</Value></Header>");
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().get(1), "<Header><Name>operation</Name><Value>foo2</Value></Header>");
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderResources().size(), 0L);

        action = ((ReceiveMessageAction)test.getActions().get(1));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());

        Assert.assertTrue(action.getMessageBuilder() instanceof StaticMessageContentBuilder);
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getMessage().getPayload(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderData().size(), 2L);
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderData().get(0), "<Header><Name>operation</Name><Value>foo1</Value></Header>");
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderData().get(1), "<Header><Name>operation</Name><Value>foo2</Value></Header>");
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderResources().size(), 0L);

    }

    @Test
    public void testReceiveBuilderWithHeaderFragment() {
        reset(referenceResolver, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage()
                        .addHeaderData("<TestRequest><Message>Hello Citrus!</Message></TestRequest>")
                        .setHeader("operation", "foo"));

        when(referenceResolver.resolve(TestContext.class)).thenReturn(context);
        when(referenceResolver.resolve(TestActionListeners.class)).thenReturn(new TestActionListeners());
        when(referenceResolver.resolveAll(SequenceBeforeTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.resolveAll(SequenceAfterTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.resolveAll(Marshaller.class)).thenReturn(Collections.<String, Marshaller>singletonMap("marshaller", marshaller));
        when(referenceResolver.resolve(Marshaller.class)).thenReturn(marshaller);

        context.setReferenceResolver(referenceResolver);
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .headerFragment(payloadModel));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getValidationContexts().size(), 3);
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(HeaderValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(XmlMessageValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(JsonMessageValidationContext.class::isInstance));

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().size(), 1L);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().get(0),
                "<TestRequest><Message>Hello Citrus!</Message></TestRequest>");

    }

    @Test
    public void testReceiveBuilderWithHeaderFragmentExplicitMarshaller() {
        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage()
                        .addHeaderData("<TestRequest><Message>Hello Citrus!</Message></TestRequest>")
                        .setHeader("operation", "foo"));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .headerFragment(payloadModel, marshaller));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getValidationContexts().size(), 3);
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(HeaderValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(XmlMessageValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(JsonMessageValidationContext.class::isInstance));

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().size(), 1L);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().get(0), "<TestRequest><Message>Hello Citrus!</Message></TestRequest>");

    }

    @Test
    public void testReceiveBuilderWithHeaderFragmentExplicitMarshallerName() {
        reset(referenceResolver, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage()
                        .addHeaderData("<TestRequest><Message>Hello Citrus!</Message></TestRequest>")
                        .setHeader("operation", "foo"));

        when(referenceResolver.resolve(TestContext.class)).thenReturn(context);
        when(referenceResolver.resolve(TestActionListeners.class)).thenReturn(new TestActionListeners());
        when(referenceResolver.resolveAll(SequenceBeforeTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.resolveAll(SequenceAfterTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.isResolvable("myMarshaller")).thenReturn(true);
        when(referenceResolver.resolve("myMarshaller")).thenReturn(marshaller);

        context.setReferenceResolver(referenceResolver);
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .headerFragment(payloadModel, "myMarshaller"));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getValidationContexts().size(), 3);
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(HeaderValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(XmlMessageValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(JsonMessageValidationContext.class::isInstance));

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().size(), 1L);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().get(0), "<TestRequest><Message>Hello Citrus!</Message></TestRequest>");

    }

    @Test
    public void testReceiveBuilderWithHeaderResource() throws IOException {
        reset(resource, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong()))
                .thenReturn(new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("operation", "foo")
                        .addHeaderData("<Header><Name>operation</Name><Value>foo</Value></Header>"))
                .thenReturn(new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("operation", "bar")
                        .addHeaderData("<Header><Name>operation</Name><Value>bar</Value></Header>"));

        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("<Header><Name>operation</Name><Value>foo</Value></Header>".getBytes()))
                                       .thenReturn(new ByteArrayInputStream("<Header><Name>operation</Name><Value>bar</Value></Header>".getBytes()));

        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .header(resource));

        runner.run(receive(messageEndpoint)
                        .message(new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>"))
                        .header(resource));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 2);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);
        Assert.assertEquals(test.getActions().get(1).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().size(), 1L);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().get(0), "<Header><Name>operation</Name><Value>foo</Value></Header>");

        action = ((ReceiveMessageAction)test.getActions().get(1));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());

        Assert.assertTrue(action.getMessageBuilder() instanceof StaticMessageContentBuilder);
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getMessage().getPayload(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderData().size(), 1L);
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderData().get(0), "<Header><Name>operation</Name><Value>bar</Value></Header>");

    }

    @Test
    public void testReceiveBuilderWithMultipleHeaderResource() throws IOException {
        reset(resource, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("operation", "foo")
                        .addHeaderData("<Header><Name>operation</Name><Value>sayHello</Value></Header>")
                        .addHeaderData("<Header><Name>operation</Name><Value>foo</Value></Header>")
                        .addHeaderData("<Header><Name>operation</Name><Value>bar</Value></Header>"));

        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream("<Header><Name>operation</Name><Value>foo</Value></Header>".getBytes()))
                                       .thenReturn(new ByteArrayInputStream("<Header><Name>operation</Name><Value>bar</Value></Header>".getBytes()))
                                       .thenReturn(new ByteArrayInputStream("<Header><Name>operation</Name><Value>foo</Value></Header>".getBytes()))
                                       .thenReturn(new ByteArrayInputStream("<Header><Name>operation</Name><Value>bar</Value></Header>".getBytes()));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .header("<Header><Name>operation</Name><Value>sayHello</Value></Header>")
                        .header(resource)
                        .header(resource));

        runner.run(receive(messageEndpoint)
                        .message(new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>"))
                        .header("<Header><Name>operation</Name><Value>sayHello</Value></Header>")
                        .header(resource)
                        .header(resource));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 2);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);
        Assert.assertEquals(test.getActions().get(1).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder) action.getMessageBuilder()).getPayloadData(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(((PayloadTemplateMessageBuilder) action.getMessageBuilder()).getHeaderData().size(), 3L);
        Assert.assertEquals(((PayloadTemplateMessageBuilder) action.getMessageBuilder()).getHeaderData().get(0), "<Header><Name>operation</Name><Value>sayHello</Value></Header>");
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().get(1), "<Header><Name>operation</Name><Value>foo</Value></Header>");
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getHeaderData().get(2), "<Header><Name>operation</Name><Value>bar</Value></Header>");

        action = ((ReceiveMessageAction)test.getActions().get(1));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());

        Assert.assertTrue(action.getMessageBuilder() instanceof StaticMessageContentBuilder);
        Assert.assertEquals(((StaticMessageContentBuilder) action.getMessageBuilder()).getMessage().getPayload(), "<TestRequest><Message>Hello World!</Message></TestRequest>");
        Assert.assertEquals(((StaticMessageContentBuilder) action.getMessageBuilder()).getHeaderData().size(), 3L);
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderData().get(0), "<Header><Name>operation</Name><Value>sayHello</Value></Header>");
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderData().get(1), "<Header><Name>operation</Name><Value>foo</Value></Header>");
        Assert.assertEquals(((StaticMessageContentBuilder)action.getMessageBuilder()).getHeaderData().get(2), "<Header><Name>operation</Name><Value>bar</Value></Header>");

    }

    @Test
    public void testReceiveBuilderWithValidator() {
        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(new DefaultMessage("TestMessage").setHeader("operation", "sayHello"));
        final TextEqualsMessageValidator validator = new TextEqualsMessageValidator();

        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .messageType(MessageType.PLAINTEXT)
                        .payload("TestMessage")
                        .header("operation", "sayHello")
                        .validator(validator));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.PLAINTEXT.name());
        Assert.assertEquals(action.getValidators().size(), 1L);
        Assert.assertEquals(action.getValidators().get(0), validator);

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder) action.getMessageBuilder()).getPayloadData(), "TestMessage");
        Assert.assertTrue(((PayloadTemplateMessageBuilder) action.getMessageBuilder()).getMessageHeaders().containsKey("operation"));

    }

    @Test
    public void testReceiveBuilderWithValidatorName() {
        final TextEqualsMessageValidator validator = new TextEqualsMessageValidator();

        reset(referenceResolver, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(new DefaultMessage("TestMessage").setHeader("operation", "sayHello"));

        when(referenceResolver.resolve(TestContext.class)).thenReturn(context);
        when(referenceResolver.resolve("plainTextValidator", MessageValidator.class)).thenReturn(validator);
        when(referenceResolver.resolve(TestActionListeners.class)).thenReturn(new TestActionListeners());
        when(referenceResolver.resolveAll(SequenceBeforeTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.resolveAll(SequenceAfterTest.class)).thenReturn(new HashMap<>());

        context.setReferenceResolver(referenceResolver);
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .messageType(MessageType.PLAINTEXT)
                        .payload("TestMessage")
                        .header("operation", "sayHello")
                        .validator("plainTextValidator"));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.PLAINTEXT.name());
        Assert.assertEquals(action.getValidators().size(), 1L);
        Assert.assertEquals(action.getValidators().get(0), validator);

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "TestMessage");
        Assert.assertTrue(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getMessageHeaders().containsKey("operation"));
    }

    @Test
    public void testReceiveBuilderWithDictionary() {
        final SimpleMappingDictionary dictionary = new SimpleMappingDictionary();

        reset(referenceResolver, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(new DefaultMessage("TestMessage").setHeader("operation", "sayHello"));

        when(referenceResolver.resolve(TestContext.class)).thenReturn(context);
        when(referenceResolver.resolve(TestActionListeners.class)).thenReturn(new TestActionListeners());
        when(referenceResolver.resolveAll(SequenceBeforeTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.resolveAll(SequenceAfterTest.class)).thenReturn(new HashMap<>());

        context.setReferenceResolver(referenceResolver);
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                                .messageType(MessageType.PLAINTEXT)
                                .payload("TestMessage")
                                .header("operation", "sayHello")
                                .dictionary(dictionary));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.PLAINTEXT.name());
        Assert.assertEquals(action.getDataDictionary(), dictionary);

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "TestMessage");
        Assert.assertTrue(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getMessageHeaders().containsKey("operation"));
    }

    @Test
    public void testReceiveBuilderWithDictionaryName() {
        final SimpleMappingDictionary dictionary = new SimpleMappingDictionary();

        reset(referenceResolver, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(new DefaultMessage("TestMessage").setHeader("operation", "sayHello"));

        when(referenceResolver.resolve(TestContext.class)).thenReturn(context);
        when(referenceResolver.resolve("customDictionary", DataDictionary.class)).thenReturn(dictionary);
        when(referenceResolver.resolve(TestActionListeners.class)).thenReturn(new TestActionListeners());
        when(referenceResolver.resolveAll(SequenceBeforeTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.resolveAll(SequenceAfterTest.class)).thenReturn(new HashMap<>());

        context.setReferenceResolver(referenceResolver);
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                                .messageType(MessageType.PLAINTEXT)
                                .payload("TestMessage")
                                .header("operation", "sayHello")
                                .dictionary("customDictionary"));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.PLAINTEXT.name());
        Assert.assertEquals(action.getDataDictionary(), dictionary);

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getPayloadData(), "TestMessage");
        Assert.assertTrue(((PayloadTemplateMessageBuilder)action.getMessageBuilder()).getMessageHeaders().containsKey("operation"));
    }

    @Test
    public void testReceiveBuilderWithSelector() {
        SelectiveConsumer selectiveConsumer = Mockito.mock(SelectiveConsumer.class);

        reset(messageEndpoint, selectiveConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(selectiveConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(selectiveConsumer.receive(eq("operation = 'sayHello'"), any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("operation", "sayHello"));
        final Map<String, String> messageSelector = new HashMap<>();
        messageSelector.put("operation", "sayHello");

        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                                .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                                .selector(messageSelector));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);

        Assert.assertEquals(action.getMessageSelectorMap(), messageSelector);

    }

    @Test
    public void testReceiveBuilderWithSelectorExpression() {
        SelectiveConsumer selectiveConsumer = Mockito.mock(SelectiveConsumer.class);

        reset(messageEndpoint, selectiveConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(selectiveConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(selectiveConsumer.receive(eq("operation = 'sayHello'"), any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message>Hello World!</Message></TestRequest>")
                        .setHeader("operation", "sayHello"));
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                                .payload("<TestRequest><Message>Hello World!</Message></TestRequest>")
                                .selector("operation = 'sayHello'"));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);

        Assert.assertTrue(action.getMessageSelectorMap().isEmpty());
        Assert.assertEquals(action.getMessageSelector(), "operation = 'sayHello'");

    }

    @Test
    public void testReceiveBuilderExtractFromPayload() {
        VariableExtractor extractor = (message, context) -> context.setVariable("messageId", message.getId());

        Message received = new DefaultMessage("<TestRequest><Message lang=\"ENG\">Hello World!</Message></TestRequest>")
                .setHeader("operation", "sayHello");
        reset(referenceResolver, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(received);

        when(referenceResolver.resolve(TestContext.class)).thenReturn(context);
        when(referenceResolver.resolve(TestActionListeners.class)).thenReturn(new TestActionListeners());
        when(referenceResolver.resolveAll(SequenceBeforeTest.class)).thenReturn(new HashMap<>());
        when(referenceResolver.resolveAll(SequenceAfterTest.class)).thenReturn(new HashMap<>());

        context.setReferenceResolver(referenceResolver);
        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                                .payload("<TestRequest><Message lang=\"ENG\">Hello World!</Message></TestRequest>")
                                .extract(extractor));

        Assert.assertNotNull(context.getVariable("messageId"));
        Assert.assertEquals(context.getVariable("messageId"), received.getId());

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);

        Assert.assertEquals(action.getVariableExtractors().size(), 1);
        Assert.assertEquals(action.getVariableExtractors().get(0), extractor);
    }

    @Test
    public void testReceiveBuilderExtractFromHeader() {
        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("<TestRequest><Message lang=\"ENG\">Hello World!</Message></TestRequest>")
                        .setHeader("operation", "sayHello")
                        .setHeader("requestId", "123456"));

        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                                .payload("<TestRequest><Message lang=\"ENG\">Hello World!</Message></TestRequest>")
                                .extract(headerValueExtractor()
                                        .header("operation", "operationHeader")
                                        .header("requestId", "id")));

        Assert.assertNotNull(context.getVariable("operationHeader"));
        Assert.assertNotNull(context.getVariable("id"));
        Assert.assertEquals(context.getVariable("operationHeader"), "sayHello");
        Assert.assertEquals(context.getVariable("id"), "123456");

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);

        Assert.assertEquals(action.getVariableExtractors().size(), 1);
        Assert.assertTrue(action.getVariableExtractors().get(0) instanceof MessageHeaderVariableExtractor);
        Assert.assertTrue(((MessageHeaderVariableExtractor) action.getVariableExtractors().get(0)).getHeaderMappings().containsKey("operation"));
        Assert.assertTrue(((MessageHeaderVariableExtractor) action.getVariableExtractors().get(0)).getHeaderMappings().containsKey("requestId"));

    }

    @Test
    public void testReceiveBuilderExtractCombined() {
        VariableExtractor extractor = (message, context) -> context.setVariable("messageId", message.getId());

        Message received = new DefaultMessage("<TestRequest><Message lang=\"ENG\">Hello World!</Message></TestRequest>")
                .setHeader("operation", "sayHello")
                .setHeader("requestId", "123456");

        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(received);

        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                                .payload("<TestRequest><Message lang=\"ENG\">Hello World!</Message></TestRequest>")
                                .extract(headerValueExtractor()
                                        .header("operation", "operationHeader")
                                        .header("requestId", "id"))
                                .extract(extractor));

        Assert.assertNotNull(context.getVariable("operationHeader"));
        Assert.assertNotNull(context.getVariable("id"));
        Assert.assertEquals(context.getVariable("operationHeader"), "sayHello");
        Assert.assertEquals(context.getVariable("id"), "123456");

        Assert.assertNotNull(context.getVariable("messageId"));
        Assert.assertEquals(context.getVariable("messageId"), received.getId());

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getMessageType(), MessageType.XML.name());
        Assert.assertEquals(action.getEndpoint(), messageEndpoint);

        Assert.assertEquals(action.getVariableExtractors().size(), 2);
        Assert.assertTrue(action.getVariableExtractors().get(0) instanceof MessageHeaderVariableExtractor);
        Assert.assertTrue(((MessageHeaderVariableExtractor) action.getVariableExtractors().get(0)).getHeaderMappings().containsKey("operation"));
        Assert.assertTrue(((MessageHeaderVariableExtractor) action.getVariableExtractors().get(0)).getHeaderMappings().containsKey("requestId"));

        Assert.assertEquals(action.getVariableExtractors().get(1), extractor);

    }

    @Test
    public void testReceiveBuilderWithValidationCallback() {
        final AbstractValidationCallback<?> callback = Mockito.mock(AbstractValidationCallback.class);

        reset(callback, messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(new DefaultMessage("TestMessage").setHeader("operation", "sayHello"));

        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                                .messageType(MessageType.PLAINTEXT)
                                .payload("TestMessage")
                                .header("operation", "sayHello")
                                .validationCallback(callback));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getMessageType(), MessageType.PLAINTEXT.name());
        Assert.assertEquals(action.getValidationCallback(), callback);

        Assert.assertTrue(action.getMessageBuilder() instanceof PayloadTemplateMessageBuilder);
        Assert.assertEquals(((PayloadTemplateMessageBuilder) action.getMessageBuilder()).getPayloadData(), "TestMessage");
        Assert.assertTrue(((PayloadTemplateMessageBuilder) action.getMessageBuilder()).getMessageHeaders().containsKey("operation"));

        verify(callback, atLeastOnce()).setReferenceResolver(context.getReferenceResolver());
        verify(callback).validate(any(Message.class), any(TestContext.class));
    }

    @Test
    public void testDeactivateSchemaValidation() throws IOException {

        reset(messageEndpoint, messageConsumer, configuration);
        when(messageEndpoint.createConsumer()).thenReturn(messageConsumer);
        when(messageEndpoint.getEndpointConfiguration()).thenReturn(configuration);
        when(configuration.getTimeout()).thenReturn(100L);
        when(messageEndpoint.getActor()).thenReturn(null);
        when(messageConsumer.receive(any(TestContext.class), anyLong())).thenReturn(
                new DefaultMessage("{}")
                        .setHeader("operation", "sayHello"));

        DefaultTestCaseRunner runner = new DefaultTestCaseRunner(context);
        runner.run(receive(messageEndpoint)
                        .payload("{}")
                        .validate(xml()
                                .schemaValidation(false)));

        TestCase test = runner.getTestCase();
        Assert.assertEquals(test.getActionCount(), 1);
        Assert.assertEquals(test.getActions().get(0).getClass(), ReceiveMessageAction.class);

        ReceiveMessageAction action = ((ReceiveMessageAction)test.getActions().get(0));
        Assert.assertEquals(action.getName(), "receive");

        Assert.assertEquals(action.getEndpoint(), messageEndpoint);
        Assert.assertEquals(action.getValidationContexts().size(), 2);
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(HeaderValidationContext.class::isInstance));
        Assert.assertTrue(action.getValidationContexts().stream().anyMatch(XmlMessageValidationContext.class::isInstance));

        XmlMessageValidationContext xmlMessageValidationContext = action.getValidationContexts().stream()
                .filter(XmlMessageValidationContext.class::isInstance).findFirst()
                .map(XmlMessageValidationContext.class::cast)
                .orElseThrow(() -> new AssertionError("Missing validation context"));
        Assert.assertFalse(xmlMessageValidationContext.isSchemaValidationEnabled());
    }
}