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
import org.mobicents.protocols.mgcp.jain.pkg.AUMgcpEvent;
import org.mobicents.protocols.mgcp.jain.pkg.AUPackage;
import org.restcomm.connect.commons.dao.CollectedResult;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.patterns.Observing;
import org.restcomm.connect.commons.patterns.StopObserving;

import java.net.URI;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

                String driver = "Google-driver";
                long timeAfterSpeech = 10;

                AsrwgsSignal asr = new AsrwgsSignal(driver, Collections.singletonList(URI.create("hello.wav")), "#", 10, 10, timeAfterSpeech, ASR_RESULT_TEXT);
                endpoint.tell(asr, observer);
                final IvrEndpointResponse<CollectedResult> ivrResponse = expectMsgClass(IvrEndpointResponse.class);
                assertTrue(ivrResponse.succeeded());
                assertTrue(ASR_RESULT_TEXT.equals(ivrResponse.get().getResult()));
                assertTrue(ivrResponse.get().isAsr());

                final IvrEndpointResponse<CollectedResult> ivrResponse2 = expectMsgClass(IvrEndpointResponse.class);
                assertTrue(ivrResponse2.succeeded());
                assertTrue(ASR_RESULT_TEXT.equals(ivrResponse2.get().getResult()));
                assertTrue(ivrResponse2.get().isAsr());


                final IvrEndpointResponse<CollectedResult> ivrResponse3 = expectMsgClass(IvrEndpointResponse.class);
                assertTrue(ivrResponse3.succeeded());
                assertNull(ivrResponse3.get().getResult());
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

                String driver = "Google-driver";
                long timeAfterSpeech = 10;

                AsrwgsSignal asr = new AsrwgsSignal(driver, Collections.singletonList(URI.create("hello.wav")), "#", 10, 10, timeAfterSpeech, ASR_RESULT_TEXT);
                endpoint.tell(asr, observer);
                final IvrEndpointResponse<CollectedResult> ivrResponse = expectMsgClass(IvrEndpointResponse.class);
                assertFalse(ivrResponse.succeeded());
                //assertTrue(ASR_RESULT_TEXT.equals(ivrResponse.get().getResult()));
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

        private Notify createNotify(final NotificationRequest request, int transactionId, final MgcpEvent event) {
            final EventName[] events = {new EventName(AUPackage.AU, event)};
            Notify notify = new Notify(this, request.getEndpointIdentifier(), request.getRequestIdentifier(), events);
            notify.setTransactionHandle(transactionId);
            return notify;
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
                Notify notify = createNotify(request, (int) transactionIdPool.get(), AUMgcpEvent.auoc.withParm("rc=101 asrr=" + ASR_RESULT_TEXT));
                sender.tell(notify, self);

                notify = createNotify(request, (int) transactionIdPool.get(), AUMgcpEvent.auoc.withParm("rc=101 asrr=" + ASR_RESULT_TEXT));
                sender.tell(notify, self);

                notify = createNotify(request, (int) transactionIdPool.get(), AUMgcpEvent.auoc.withParm("rc=100"));
                sender.tell(notify, self);
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
                MgcpEvent asrFailEvent = AUMgcpEvent.auof.withParm("rc=300 asrr=" + ASR_RESULT_TEXT);
                final EventName[] events = {new EventName(AUPackage.AU, asrFailEvent)};
                final Notify notify = new Notify(this, request.getEndpointIdentifier(), request.getRequestIdentifier(), events);
                notify.setTransactionHandle((int) transactionIdPool.get());
                sender.tell(notify, self);
                System.out.println(notify.toString());
            }
        }
    }
}
