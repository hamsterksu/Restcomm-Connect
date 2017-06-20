package org.restcomm.connect.mgcp;

import jain.protocol.ip.mgcp.message.parms.EventName;
import jain.protocol.ip.mgcp.message.parms.RequestedAction;
import jain.protocol.ip.mgcp.message.parms.RequestedEvent;
import jain.protocol.ip.mgcp.pkg.MgcpEvent;
import jain.protocol.ip.mgcp.pkg.PackageName;
import org.restcomm.connect.commons.annotations.concurrency.Immutable;
import org.snmp4j.smi.OctetString;

import java.net.URI;
import java.util.List;

/**
 * Created by hamsterksu on 6/6/17.
 */
@Immutable
public class AsrwgsSignal {

    public static final PackageName PACKAGE_NAME = PackageName.factory("ASR", 166);
    public static final MgcpEvent REQUEST_ASRWGS = MgcpEvent.factory("asrwgs", 166 + 3);
    public static final MgcpEvent EVENT_ASRSUCC = MgcpEvent.factory("asrsucc", 166 + 2);
    public static final MgcpEvent EVENT_ASRFAIL = MgcpEvent.factory("asrfail", 166 + 1);

    public static final RequestedEvent[] REQUESTED_EVENTS = new RequestedEvent[2];

    static {
        final RequestedAction[] action = new RequestedAction[]{RequestedAction.NotifyImmediately};
        REQUESTED_EVENTS[0] = new RequestedEvent(new EventName(PACKAGE_NAME, EVENT_ASRSUCC), action);
        REQUESTED_EVENTS[1] = new RequestedEvent(new EventName(PACKAGE_NAME, EVENT_ASRFAIL), action);
    }

    private static final String SPACE_CHARACTER = " ";

    private final String driver;
    private final List<URI> initialPrompts;
    private final String endInputKey;
    private final long maximumRecTimer;
    private final long waitingInputTimer;
    private final long timeAfterSpeech;
    private final String hotWords;

    public AsrwgsSignal(String driver, List<URI> initialPrompts, String endInputKey, long maximumRecTimer, long waitingInputTimer,
                        long timeAfterSpeech, String hotWords) {
        this.driver = driver;
        this.initialPrompts = initialPrompts;
        this.endInputKey = endInputKey;
        this.maximumRecTimer = maximumRecTimer;
        this.waitingInputTimer = waitingInputTimer;
        this.timeAfterSpeech = timeAfterSpeech;
        this.hotWords = hotWords;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        if (!initialPrompts.isEmpty()) {
            buffer.append("ip=");
            for (int index = 0; index < initialPrompts.size(); index++) {
                buffer.append(initialPrompts.get(index));
                if (index < initialPrompts.size() - 1) {
                    //https://github.com/RestComm/Restcomm-Connect/issues/1988
                    buffer.append(",");
                }
            }
        }

        if (buffer.length() > 0)
            buffer.append(SPACE_CHARACTER);
        buffer.append("rgst=Other").append(SPACE_CHARACTER).append("rgsf=XML");

        if (driver != null) {
            if (buffer.length() > 0)
                buffer.append(SPACE_CHARACTER);
            buffer.append("dr=").append(driver);
        }
        if (endInputKey != null) {
            if (buffer.length() > 0)
                buffer.append(SPACE_CHARACTER);
            buffer.append("eik=").append(endInputKey);
        }
        if (maximumRecTimer > 0) {
            if (buffer.length() > 0)
                buffer.append(SPACE_CHARACTER);
            buffer.append("mrt=").append(maximumRecTimer);
        }
        if (waitingInputTimer > 0) {
            if (buffer.length() > 0)
                buffer.append(SPACE_CHARACTER);
            buffer.append("wit=").append(waitingInputTimer);
        }
        if (timeAfterSpeech > 0) {
            if (buffer.length() > 0)
                buffer.append(SPACE_CHARACTER);
            buffer.append("pst=").append(timeAfterSpeech);
        }
        if (hotWords != null) {
            if (buffer.length() > 0)
                buffer.append(SPACE_CHARACTER);
            buffer.append("hw=").append(new OctetString(hotWords).toHexString());
        }
        return buffer.toString();
    }
}
