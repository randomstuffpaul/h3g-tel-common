package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.net.LinkProperties;
import android.os.Process;
import com.android.internal.telephony.dataconnection.ApnContext;
import com.android.internal.telephony.dataconnection.ApnSetting;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class PDPContextStateBroadcaster {
    private static final String ACTION_PDP_CONTEXT_STATE = "diagandroid.data.PDPContextState";
    private static final String APN_TYPE_PRIMARY = "default";
    private static final String CONTEXT_STATE_CONNECTED = "CONNECTED";
    private static final String CONTEXT_STATE_DISCONNECTED = "DISCONNECTED";
    private static final String CONTEXT_STATE_REQUESTED = "REQUEST";
    private static final String CONTEXT_TYPE_PRIMARY = "PRIMARY";
    private static final String CONTEXT_TYPE_SECONDARY = "SECONDARY";
    private static final boolean DEBUG_LOG = false;
    private static final int DNS_EXTRA_COUNT = 2;
    private static final String EXTRA_CONTEXT_APN = "ContextAPN";
    private static final String[] EXTRA_CONTEXT_DNS = new String[]{"ContextDNS1", "ContextDNS2"};
    private static final String EXTRA_CONTEXT_ERROR_CODE = "ContextErrorCode";
    private static final String EXTRA_CONTEXT_ID = "ContextID";
    private static final String EXTRA_CONTEXT_INITIATOR = "ContextInitiator";
    private static final String EXTRA_CONTEXT_IPV4_ADDR = "ContextIPV4Addr";
    private static final String EXTRA_CONTEXT_IPV6_ADDR = "ContextIPV6Addr";
    private static final String EXTRA_CONTEXT_NSAPI = "ContextNSAPI";
    private static final String EXTRA_CONTEXT_SAPI = "ContextSAPI";
    private static final String EXTRA_CONTEXT_STATE = "ContextState";
    private static final String EXTRA_CONTEXT_TERM_CODE = "ContextTermCode";
    private static final String EXTRA_CONTEXT_TYPE = "ContextType";
    private static final String[] EXTRA_CONTEXT_V6DNS = new String[]{"ContextV6DNS1", "ContextV6DNS2"};
    private static final String INITIATOR_NETWORK = "NETWORK";
    private static final String INITIATOR_UE = "USER";
    private static final int IP_ADDRESS_TYPE_COUNT = 2;
    private static final int IP_ADDRESS_V4_INDEX = 0;
    private static final int IP_ADDRESS_V6_INDEX = 1;
    private static final String LOG_TAG = "PDPContextStateBroadcaster";
    private static final String PERMISSION_RECEIVE_PDP_CONTEXT_STATE = "diagandroid.data.receivePDPContextState";
    private static final HashSet<String> sExcludeTypes = new C00191();
    private static PDPContextStateBroadcaster sInstance;
    private static final HashMap<String, Integer> sTermCodeMap = new C00202();
    private final HashMap<Integer, HashMap<String, Integer>> mApnIdMap = new HashMap();
    private int mNextContextId = 1;

    static class C00191 extends HashSet<String> {
        C00191() {
            add("hipri");
            add("supl");
            add("ims");
        }
    }

    static class C00202 extends HashMap<String, Integer> {
        C00202() {
            put(null, new Integer(301));
            put(Phone.REASON_RADIO_TURNED_OFF, new Integer(302));
            put("unknownPdpDisconnect", new Integer(303));
            put("unknown data error", new Integer(304));
            put(Phone.REASON_ROAMING_ON, new Integer(305));
            put(Phone.REASON_ROAMING_OFF, new Integer(306));
            put(Phone.REASON_DATA_DISABLED, new Integer(307));
            put(Phone.REASON_DATA_ENABLED, new Integer(308));
            put(Phone.REASON_DATA_ATTACHED, new Integer(309));
            put(Phone.REASON_DATA_DETACHED, new Integer(310));
            put(Phone.REASON_CDMA_DATA_ATTACHED, new Integer(311));
            put(Phone.REASON_CDMA_DATA_DETACHED, new Integer(312));
            put(Phone.REASON_APN_CHANGED, new Integer(313));
            put(Phone.REASON_APN_SWITCHED, new Integer(314));
            put(Phone.REASON_APN_FAILED, new Integer(315));
            put(Phone.REASON_RESTORE_DEFAULT_APN, new Integer(316));
            put(Phone.REASON_PDP_RESET, new Integer(317));
            put(Phone.REASON_VOICE_CALL_ENDED, new Integer(318));
            put(Phone.REASON_VOICE_CALL_STARTED, new Integer(319));
            put(Phone.REASON_PS_RESTRICT_ENABLED, new Integer(320));
            put(Phone.REASON_PS_RESTRICT_DISABLED, new Integer(321));
            put(Phone.REASON_SIM_LOADED, new Integer(322));
            put("apnTypeDisabled", new Integer(323));
            put("apnTypeEnabled", new Integer(324));
            put("masterDataDisabled", new Integer(325));
            put("masterDataEnabled", new Integer(326));
            put("iccRecordsLoaded", new Integer(327));
            put("cdmaOtaProvisioning", new Integer(328));
            put("defaultDataDisabled", new Integer(329));
            put("defaultDataEnabled", new Integer(330));
            put("radioOn", new Integer(331));
            put("radioOff", new Integer(332));
            put("radioTechnologyChanged", new Integer(333));
            put("networkOrModemDisconnect", new Integer(334));
            put("dataNetworkAttached", new Integer(335));
            put("dataNetworkDetached", new Integer(336));
            put("dataProfileDbChanged", new Integer(337));
            put("cdmaSubscriptionSourceChanged", new Integer(338));
            put("tetheredModeChanged", new Integer(339));
            put("dataConnectionPropertyChanged", new Integer(340));
            put(Phone.REASON_NW_TYPE_CHANGED, new Integer(301));
            put(Phone.REASON_DATA_DEPENDENCY_MET, new Integer(301));
            put(Phone.REASON_DATA_DEPENDENCY_UNMET, new Integer(301));
            put("linkPropertiesChanged", new Integer(301));
        }
    }

    public static synchronized void enable() {
        synchronized (PDPContextStateBroadcaster.class) {
            if (sInstance == null) {
                sInstance = new PDPContextStateBroadcaster();
            }
        }
    }

    public static synchronized void disable() {
        synchronized (PDPContextStateBroadcaster.class) {
            sInstance = null;
        }
    }

    public static synchronized void sendRequested(Context context, ApnContext apnContext) {
        synchronized (PDPContextStateBroadcaster.class) {
            if (shouldReport(apnContext)) {
                try {
                    sInstance.sendPDPContextRequested(context, apnContext);
                } catch (Exception e) {
                }
            }
        }
    }

    public static synchronized void sendConnected(Context context, ApnContext apnContext) {
        synchronized (PDPContextStateBroadcaster.class) {
            if (shouldReport(apnContext)) {
                try {
                    LinkProperties linkProperties = apnContext.getDcAc().getLinkPropertiesSync();
                    if (linkProperties != null) {
                        String[] ipAddresses = new String[]{"", ""};
                        String[] ipv4DNS = new String[]{"", ""};
                        String[] ipv6DNS = new String[]{"", ""};
                        processIPAddresses(linkProperties.getAddresses(), ipAddresses);
                        processDNSAddresses(linkProperties.getDnses(), ipv4DNS, ipv6DNS);
                        sInstance.sendPDPContextConnected(context, apnContext, ipAddresses[0], ipAddresses[1], ipv4DNS, ipv6DNS);
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    public static synchronized void sendDisconnected(Context context, ApnContext apnContext) {
        synchronized (PDPContextStateBroadcaster.class) {
            if (shouldReport(apnContext)) {
                try {
                    sInstance.sendPDPContextDisconnected(context, apnContext, apnContext.getReason());
                } catch (Exception e) {
                }
            }
        }
    }

    public static synchronized void sendDisconnected(Context context, ApnContext apnContext, String reason) {
        synchronized (PDPContextStateBroadcaster.class) {
            if (shouldReport(apnContext)) {
                try {
                    sInstance.sendPDPContextDisconnected(context, apnContext, reason);
                } catch (Exception e) {
                }
            }
        }
    }

    private Integer getContextId(ApnContext apnContext) {
        HashMap<String, Integer> apnTypeIdMap = (HashMap) this.mApnIdMap.get(Integer.valueOf(apnContext.getApnSetting().id));
        if (apnTypeIdMap != null) {
            return (Integer) apnTypeIdMap.get(apnContext.getApnType());
        }
        return null;
    }

    private void removeContextId(ApnContext apnContext) {
        Integer apnIdValue = Integer.valueOf(apnContext.getApnSetting().id);
        HashMap<String, Integer> apnTypeIdMap = (HashMap) this.mApnIdMap.get(apnIdValue);
        if (apnTypeIdMap != null) {
            apnTypeIdMap.remove(apnContext.getApnType());
            if (apnTypeIdMap.isEmpty()) {
                this.mApnIdMap.remove(apnIdValue);
            }
        }
    }

    private boolean contextIdInUse(int contextId) {
        Integer contextIdValue = Integer.valueOf(contextId);
        for (HashMap<String, Integer> apnTypeIdMap : this.mApnIdMap.values()) {
            if (apnTypeIdMap.containsValue(contextIdValue)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldReport(ApnContext apnContext) {
        return (sInstance == null || apnContext == null || apnContext.getApnSetting() == null || sExcludeTypes.contains(apnContext.getApnType())) ? false : true;
    }

    private PDPContextStateBroadcaster() {
    }

    private int getNextContextId() {
        int nextId;
        do {
            nextId = this.mNextContextId;
            this.mNextContextId = nextId + 1;
            if (this.mNextContextId > 65535) {
                this.mNextContextId = 1;
            }
        } while (contextIdInUse(nextId));
        return nextId;
    }

    private static String getContextType(String apnType) {
        return APN_TYPE_PRIMARY.equals(apnType) ? CONTEXT_TYPE_PRIMARY : CONTEXT_TYPE_SECONDARY;
    }

    private static Integer getTermCode(String reason) {
        Integer termCode = (Integer) sTermCodeMap.get(reason);
        if (termCode == null) {
            return (Integer) sTermCodeMap.get(null);
        }
        return termCode;
    }

    private void sendPDPContextRequested(Context context, ApnContext apnContext) {
        ApnSetting apnSetting = apnContext.getApnSetting();
        int apnId = apnSetting.id;
        String contextApn = apnSetting.apn;
        String apnType = apnContext.getApnType();
        String contextType = getContextType(apnType);
        Integer apnIdObject = Integer.valueOf(apnId);
        HashMap<String, Integer> apnTypeIdMap = (HashMap) this.mApnIdMap.get(apnIdObject);
        if (apnTypeIdMap == null) {
            apnTypeIdMap = new HashMap();
            this.mApnIdMap.put(apnIdObject, apnTypeIdMap);
        }
        if (!apnTypeIdMap.containsKey(apnType)) {
            Integer contextId = Integer.valueOf(getNextContextId());
            apnTypeIdMap.put(apnType, contextId);
            Intent intent = createIntent(CONTEXT_STATE_REQUESTED, contextId);
            intent.putExtra(EXTRA_CONTEXT_INITIATOR, INITIATOR_UE);
            intent.putExtra(EXTRA_CONTEXT_TYPE, contextType);
            intent.putExtra(EXTRA_CONTEXT_NSAPI, Integer.toString(0));
            intent.putExtra(EXTRA_CONTEXT_SAPI, Integer.toString(0));
            intent.putExtra(EXTRA_CONTEXT_APN, contextApn);
            broadcast(context, intent);
        }
    }

    private void sendPDPContextConnected(Context context, ApnContext apnContext, String ipv4Address, String ipv6Address, String[] ipv4DNS, String[] ipv6DNS) {
        Integer contextId = getContextId(apnContext);
        if (contextId != null) {
            Intent intent = createIntent(CONTEXT_STATE_CONNECTED, contextId);
            intent.putExtra(EXTRA_CONTEXT_IPV4_ADDR, ipv4Address);
            intent.putExtra(EXTRA_CONTEXT_IPV6_ADDR, ipv6Address);
            for (int dnsExtraIndex = 0; dnsExtraIndex < 2; dnsExtraIndex++) {
                intent.putExtra(EXTRA_CONTEXT_DNS[dnsExtraIndex], ipv4DNS[dnsExtraIndex]);
                intent.putExtra(EXTRA_CONTEXT_V6DNS[dnsExtraIndex], ipv6DNS[dnsExtraIndex]);
            }
            broadcast(context, intent);
        }
    }

    private void sendPDPContextDisconnected(Context context, ApnContext apnContext, String reason) {
        Integer contextId = getContextId(apnContext);
        if (contextId != null) {
            removeContextId(apnContext);
            Intent intent = createIntent(CONTEXT_STATE_DISCONNECTED, contextId);
            intent.putExtra(EXTRA_CONTEXT_INITIATOR, INITIATOR_UE);
            intent.putExtra(EXTRA_CONTEXT_TERM_CODE, getTermCode(reason).toString());
            intent.putExtra(EXTRA_CONTEXT_ERROR_CODE, Integer.toString(-1));
            broadcast(context, intent);
        }
    }

    private static Intent createIntent(String contextState, Integer contextId) {
        Intent intent = new Intent(ACTION_PDP_CONTEXT_STATE);
        intent.putExtra(EXTRA_CONTEXT_STATE, contextState);
        intent.putExtra(EXTRA_CONTEXT_ID, contextId.toString());
        return intent;
    }

    private void broadcast(Context context, Intent intent) {
        context.sendBroadcastAsUser(intent, Process.myUserHandle(), PERMISSION_RECEIVE_PDP_CONTEXT_STATE);
    }

    private static void processIPAddresses(Collection<InetAddress> addresses, String[] sortedAddresses) {
        for (InetAddress address : addresses) {
            if (address instanceof Inet4Address) {
                assignToArrayElementIfEmpty(address.getHostAddress(), sortedAddresses, 0);
            } else if (address instanceof Inet6Address) {
                assignToArrayElementIfEmpty(address.getHostAddress(), sortedAddresses, 1);
            }
        }
    }

    private static void processDNSAddresses(Collection<InetAddress> addresses, String[] ipv4DNSes, String[] ipv6DNSes) {
        for (InetAddress address : addresses) {
            String[] targetArray = null;
            if (address instanceof Inet4Address) {
                targetArray = ipv4DNSes;
            } else if (address instanceof Inet6Address) {
                targetArray = ipv6DNSes;
            }
            if (targetArray != null) {
                assignToEmptyElement(address.getHostAddress(), targetArray);
            }
        }
    }

    private static void assignToEmptyElement(String value, String[] targetArray) {
        int index = 0;
        while (true) {
            int index2 = index + 1;
            if (!assignToArrayElementIfEmpty(value, targetArray, index) && index2 != targetArray.length) {
                index = index2;
            } else {
                return;
            }
        }
    }

    private static boolean assignToArrayElementIfEmpty(String value, String[] targetArray, int targetIndex) {
        boolean empty = targetArray[targetIndex].isEmpty();
        if (empty) {
            targetArray[targetIndex] = value;
        }
        return empty;
    }
}
