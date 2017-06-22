package org.restcomm.connect.mgcp;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import jain.protocol.ip.mgcp.JainMgcpEvent;
import jain.protocol.ip.mgcp.JainMgcpResponseEvent;
import jain.protocol.ip.mgcp.message.NotificationRequest;
import jain.protocol.ip.mgcp.message.NotificationRequestResponse;
import jain.protocol.ip.mgcp.message.Notify;
import jain.protocol.ip.mgcp.message.parms.EventName;
import jain.protocol.ip.mgcp.message.parms.ReturnCode;
import jain.protocol.ip.mgcp.pkg.MgcpEvent;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restcomm.connect.commons.dao.CollectedResult;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.patterns.Observing;
import org.restcomm.connect.commons.patterns.StopObserving;
import org.snmp4j.smi.OctetString;

import java.net.URI;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Dmitriy Nadolenko
 * @version 1.0
 * @since 1.0
 */
public class IvrAsrEndpointTest {

    private static final String ASR_RESULT_TEXT = "Super_text";

    private static ActorSystem system;

    public IvrAsrEndpointTest() {
        super();
    }

    @BeforeClass
    public static void before() throws Exception {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void after() throws Exception {
        system.shutdown();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSuccessfulAsrScenario() {
        //public void testSuccessfulAsrScenarioWithDigits() {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                // Create a new mock media gateway to simulate the real thing.
                final ActorRef gateway = system.actorOf(new Props(IvrAsrEndpointTest.MockAsrMediaGateway.class));
                // Create a media session. This is just an identifier that groups
                // a set of end points, connections, and lists in to one call.
                gateway.tell(new CreateMediaSession(), observer);
                final MediaGatewayResponse<MediaSession> mediaSessionResponse = expectMsgClass(MediaGatewayResponse.class);
                assertTrue(mediaSessionResponse.succeeded());
                final MediaSession session = mediaSessionResponse.get();
                // Create an IVR end point.
                gateway.tell(new CreateIvrEndpoint(session), observer);
                final MediaGatewayResponse<ActorRef> endpointResponse = expectMsgClass(MediaGatewayResponse.class);
                assertTrue(endpointResponse.succeeded());
                final ActorRef endpoint = endpointResponse.get();
                // Start observing events from the IVR end point.
                endpoint.tell(new Observe(observer), observer);
                final Observing observingResponse = expectMsgClass(Observing.class);
                assertTrue(observingResponse.succeeded());

                AsrwgsSignal asr = new AsrwgsSignal("no_name_driver", Collections.singletonList(URI.create("hello.wav")), "#", 10, 10, 10, ASR_RESULT_TEXT);
                endpoint.tell(asr, observer);
                final IvrEndpointResponse<CollectedResult> ivrResponse = expectMsgClass(IvrEndpointResponse.class);
                assertTrue(ivrResponse.succeeded());
                assertTrue(ASR_RESULT_TEXT.equals(ivrResponse.get().getResult()));
                assertTrue(ivrResponse.get().isAsr());

                final IvrEndpointResponse<CollectedResult> ivrResponse2 = expectMsgClass(IvrEndpointResponse.class);
                assertTrue(ivrResponse2.succeeded());
                assertTrue(ASR_RESULT_TEXT.equals(ivrResponse2.get().getResult()));
                assertTrue(ivrResponse2.get().isAsr());

                // Stop observing events from the IVR end point.
                endpoint.tell(new StopObserving(observer), observer);
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFailureScenario() {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                // Create a new mock media gateway to simulate the real thing.
                final ActorRef gateway = system.actorOf(new Props(IvrAsrEndpointTest.FailingMockAsrMediaGateway.class));
                // Create a media session. This is just an identifier that groups
                // a set of end points, connections, and lists in to one call.
                gateway.tell(new CreateMediaSession(), observer);
                final MediaGatewayResponse<MediaSession> mediaSessionResponse = expectMsgClass(MediaGatewayResponse.class);
                assertTrue(mediaSessionResponse.succeeded());
                final MediaSession session = mediaSessionResponse.get();
                // Create an IVR end point.
                gateway.tell(new CreateIvrEndpoint(session), observer);
                final MediaGatewayResponse<ActorRef> endpointResponse = expectMsgClass(MediaGatewayResponse.class);
                assertTrue(endpointResponse.succeeded());
                final ActorRef endpoint = endpointResponse.get();
                // Start observing events from the IVR end point.
                endpoint.tell(new Observe(observer), observer);
                final Observing observingResponse = expectMsgClass(Observing.class);
                assertTrue(observingResponse.succeeded());

                AsrwgsSignal asr = new AsrwgsSignal("no_name_driver", Collections.singletonList(URI.create("hello.wav")), "#", 10, 10, 10, ASR_RESULT_TEXT);
                endpoint.tell(asr, observer);
                final IvrEndpointResponse<CollectedResult> ivrResponse = expectMsgClass(IvrEndpointResponse.class);
                assertFalse(ivrResponse.succeeded());
                String errorMessage = "jain.protocol.ip.mgcp.JainIPMgcpException: The IVR request failed with the following error code 300";
                assertTrue(ivrResponse.cause().toString().equals(errorMessage));
                assertTrue(ivrResponse.get() == null);
            }
        };
    }

    private static final class MockAsrMediaGateway extends AbstractMockMediaGateway {
        @SuppressWarnings("unused")
        public MockAsrMediaGateway() {
            super();
        }

        @Override
        protected void event(final Object message, final ActorRef sender) {
            final ActorRef self = self();
            if (message instanceof JainMgcpEvent) {
                System.out.println(message.toString());
            }
            final Class<?> klass = message.getClass();
            if (NotificationRequest.class.equals(klass)) {
                // Send a successful response for this request.
                final NotificationRequest request = (NotificationRequest) message;
                final JainMgcpResponseEvent response = new NotificationRequestResponse(this,
                        ReturnCode.Transaction_Executed_Normally);
                sender.tell(response, self);
                System.out.println(response.toString());
                // Send the notification.

                // TODO: extend test - 100, 101, timeout, "100 + endOfKey"
                String asrResultTextHex = new OctetString(ASR_RESULT_TEXT).toHexString();
                MgcpEvent asrsucc = AsrwgsSignal.EVENT_ASRSUCC.withParm("rc=101 asrr=" + asrResultTextHex);

                final EventName[] events = {new EventName(AsrwgsSignal.PACKAGE_NAME, asrsucc)};
                final Notify notify = new Notify(this, request.getEndpointIdentifier(), request.getRequestIdentifier(), events);
                notify.setTransactionHandle((int) transactionIdPool.get());
                sender.tell(notify, self);
                System.out.println(notify.toString());

                notify.setTransactionHandle((int) transactionIdPool.get());
                sender.tell(notify, self);
                System.out.println(notify.toString());
            }
        }
    }

    private static final class FailingMockAsrMediaGateway extends AbstractMockMediaGateway {
        @SuppressWarnings("unused")
        public FailingMockAsrMediaGateway() {
            super();
        }

        @Override
        protected void event(final Object message, final ActorRef sender) {
            final ActorRef self = self();
            if (message instanceof JainMgcpEvent) {
                System.out.println(message.toString());
            }
            final Class<?> klass = message.getClass();
            if (NotificationRequest.class.equals(klass)) {
                // Send a successful response for this request.
                final NotificationRequest request = (NotificationRequest) message;
                final JainMgcpResponseEvent response = new NotificationRequestResponse(this,
                        ReturnCode.Transaction_Executed_Normally);
                response.setTransactionHandle(request.getTransactionHandle());
                sender.tell(response, self);
                System.out.println(response.toString());

                // Send the notification.
                MgcpEvent asrFailEvent = AsrwgsSignal.EVENT_ASRFAIL.withParm("rc=300 asrr=");
                final EventName[] events = {new EventName(AsrwgsSignal.PACKAGE_NAME, asrFailEvent)};
                final Notify notify = new Notify(this, request.getEndpointIdentifier(), request.getRequestIdentifier(), events);
                notify.setTransactionHandle((int) transactionIdPool.get());
                sender.tell(notify, self);
                System.out.println(notify.toString());
            }
        }
    }
}
