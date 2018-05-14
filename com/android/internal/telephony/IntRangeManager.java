package com.android.internal.telephony;

import java.util.ArrayList;
import java.util.Iterator;

public abstract class IntRangeManager {
    private static final int INITIAL_CLIENTS_ARRAY_SIZE = 4;
    private ArrayList<IntRange> mRanges = new ArrayList();

    private class ClientRange {
        final String mClient;
        final int mEndId;
        final int mStartId;

        ClientRange(int startId, int endId, String client) {
            this.mStartId = startId;
            this.mEndId = endId;
            this.mClient = client;
        }

        public boolean equals(Object o) {
            if (o == null || !(o instanceof ClientRange)) {
                return false;
            }
            ClientRange other = (ClientRange) o;
            if (this.mStartId == other.mStartId && this.mEndId == other.mEndId && this.mClient.equals(other.mClient)) {
                return true;
            }
            return false;
        }

        public int hashCode() {
            return (((this.mStartId * 31) + this.mEndId) * 31) + this.mClient.hashCode();
        }
    }

    private class IntRange {
        final ArrayList<ClientRange> mClients;
        int mEndId;
        int mStartId;

        IntRange(int startId, int endId, String client) {
            this.mStartId = startId;
            this.mEndId = endId;
            this.mClients = new ArrayList(4);
            this.mClients.add(new ClientRange(startId, endId, client));
        }

        IntRange(ClientRange clientRange) {
            this.mStartId = clientRange.mStartId;
            this.mEndId = clientRange.mEndId;
            this.mClients = new ArrayList(4);
            this.mClients.add(clientRange);
        }

        IntRange(IntRange intRange, int numElements) {
            this.mStartId = intRange.mStartId;
            this.mEndId = intRange.mEndId;
            this.mClients = new ArrayList(intRange.mClients.size());
            for (int i = 0; i < numElements; i++) {
                this.mClients.add(intRange.mClients.get(i));
            }
        }

        void insert(ClientRange range) {
            int len = this.mClients.size();
            int insert = -1;
            for (int i = 0; i < len; i++) {
                ClientRange nextRange = (ClientRange) this.mClients.get(i);
                if (range.mStartId <= nextRange.mStartId) {
                    if (!range.equals(nextRange)) {
                        if (range.mStartId == nextRange.mStartId && range.mEndId > nextRange.mEndId) {
                            insert = i + 1;
                            if (insert >= len) {
                                break;
                            }
                        } else {
                            this.mClients.add(i, range);
                            return;
                        }
                    }
                    return;
                }
            }
            if (insert == -1 || insert >= len) {
                this.mClients.add(range);
            } else {
                this.mClients.add(insert, range);
            }
        }
    }

    protected abstract void addRange(int i, int i2, boolean z);

    protected abstract boolean finishUpdate();

    protected abstract void startUpdate();

    protected IntRangeManager() {
    }

    public synchronized boolean enableRange(int startId, int endId, String client) {
        boolean z;
        int len = this.mRanges.size();
        int i;
        if (startId == 0 && endId == 999) {
            for (i = 0; i < len; i++) {
                ArrayList<ClientRange> clients = ((IntRange) this.mRanges.get(i)).mClients;
                int crLength = clients.size();
                int crIndex = 0;
                while (crIndex < crLength) {
                    ClientRange cr = (ClientRange) clients.get(crIndex);
                    if (cr.mStartId != startId || cr.mEndId != endId || !cr.mClient.equals(client)) {
                        crIndex++;
                    } else if (updateRanges()) {
                        z = true;
                    } else {
                        z = false;
                    }
                }
            }
            tryAddRanges(startId, endId, true);
            this.mRanges.add(new IntRange(startId, endId, client));
            z = true;
        } else if (len != 0) {
            int startIndex = 0;
            while (startIndex < len) {
                IntRange range = (IntRange) this.mRanges.get(startIndex);
                if (startId >= range.mStartId && endId <= range.mEndId) {
                    range.insert(new ClientRange(startId, endId, client));
                    z = true;
                    break;
                }
                int newRangeEndId;
                if (startId - 1 == range.mEndId) {
                    newRangeEndId = endId;
                    IntRange nextRange = null;
                    if (startIndex + 1 < len) {
                        nextRange = (IntRange) this.mRanges.get(startIndex + 1);
                        if (nextRange.mStartId - 1 <= endId) {
                            if (endId <= nextRange.mEndId) {
                                newRangeEndId = nextRange.mStartId - 1;
                            }
                        } else {
                            nextRange = null;
                        }
                    }
                    if (tryAddRanges(startId, newRangeEndId, true)) {
                        range.mEndId = endId;
                        range.insert(new ClientRange(startId, endId, client));
                        if (nextRange != null && range.mEndId < nextRange.mEndId) {
                            range.mEndId = nextRange.mEndId;
                            range.mClients.addAll(nextRange.mClients);
                            this.mRanges.remove(nextRange);
                        }
                        z = true;
                    } else {
                        z = false;
                    }
                } else {
                    int endIndex;
                    IntRange endRange;
                    int joinIndex;
                    IntRange joinRange;
                    if (startId < range.mStartId) {
                        if (endId + 1 >= range.mStartId) {
                            if (endId > range.mEndId) {
                                endIndex = startIndex + 1;
                                while (endIndex < len) {
                                    endRange = (IntRange) this.mRanges.get(endIndex);
                                    if (endId + 1 >= endRange.mStartId) {
                                        if (endId > endRange.mEndId) {
                                            endIndex++;
                                        } else if (tryAddRanges(startId, endRange.mStartId - 1, true)) {
                                            range.mStartId = startId;
                                            range.mEndId = endRange.mEndId;
                                            range.mClients.add(0, new ClientRange(startId, endId, client));
                                            joinIndex = startIndex + 1;
                                            for (i = joinIndex; i <= endIndex; i++) {
                                                joinRange = (IntRange) this.mRanges.get(joinIndex);
                                                range.mClients.addAll(joinRange.mClients);
                                                this.mRanges.remove(joinRange);
                                            }
                                            z = true;
                                        } else {
                                            z = false;
                                        }
                                    } else if (tryAddRanges(startId, endId, true)) {
                                        range.mStartId = startId;
                                        range.mEndId = endId;
                                        range.mClients.add(0, new ClientRange(startId, endId, client));
                                        joinIndex = startIndex + 1;
                                        for (i = joinIndex; i < endIndex; i++) {
                                            joinRange = (IntRange) this.mRanges.get(joinIndex);
                                            range.mClients.addAll(joinRange.mClients);
                                            this.mRanges.remove(joinRange);
                                        }
                                        z = true;
                                    } else {
                                        z = false;
                                    }
                                }
                                if (tryAddRanges(startId, endId, true)) {
                                    range.mStartId = startId;
                                    range.mEndId = endId;
                                    range.mClients.add(0, new ClientRange(startId, endId, client));
                                    joinIndex = startIndex + 1;
                                    for (i = joinIndex; i < len; i++) {
                                        joinRange = (IntRange) this.mRanges.get(joinIndex);
                                        range.mClients.addAll(joinRange.mClients);
                                        this.mRanges.remove(joinRange);
                                    }
                                    z = true;
                                } else {
                                    z = false;
                                }
                            } else if (tryAddRanges(startId, range.mStartId - 1, true)) {
                                range.mStartId = startId;
                                range.mClients.add(0, new ClientRange(startId, endId, client));
                                z = true;
                            } else {
                                z = false;
                            }
                        } else if (tryAddRanges(startId, endId, true)) {
                            this.mRanges.add(startIndex, new IntRange(startId, endId, client));
                            z = true;
                        } else {
                            z = false;
                        }
                    } else {
                        if (startId + 1 <= range.mEndId) {
                            if (endId <= range.mEndId) {
                                range.insert(new ClientRange(startId, endId, client));
                                z = true;
                            } else {
                                endIndex = startIndex;
                                int testIndex = startIndex + 1;
                                while (testIndex < len && endId + 1 >= ((IntRange) this.mRanges.get(testIndex)).mStartId) {
                                    endIndex = testIndex;
                                    testIndex++;
                                }
                                if (endIndex != startIndex) {
                                    endRange = (IntRange) this.mRanges.get(endIndex);
                                    if (endId <= endRange.mEndId) {
                                        newRangeEndId = endRange.mStartId - 1;
                                    } else {
                                        newRangeEndId = endId;
                                    }
                                    if (tryAddRanges(range.mEndId + 1, newRangeEndId, true)) {
                                        if (endId <= endRange.mEndId) {
                                            newRangeEndId = endRange.mEndId;
                                        } else {
                                            newRangeEndId = endId;
                                        }
                                        range.mEndId = newRangeEndId;
                                        range.insert(new ClientRange(startId, endId, client));
                                        joinIndex = startIndex + 1;
                                        for (i = joinIndex; i <= endIndex; i++) {
                                            joinRange = (IntRange) this.mRanges.get(joinIndex);
                                            range.mClients.addAll(joinRange.mClients);
                                            this.mRanges.remove(joinRange);
                                        }
                                        z = true;
                                    } else {
                                        z = false;
                                    }
                                } else if (tryAddRanges(range.mEndId + 1, endId, true)) {
                                    range.mEndId = endId;
                                    range.insert(new ClientRange(startId, endId, client));
                                    z = true;
                                } else {
                                    z = false;
                                }
                            }
                        } else {
                            startIndex++;
                        }
                    }
                }
            }
            if (tryAddRanges(startId, endId, true)) {
                this.mRanges.add(new IntRange(startId, endId, client));
                z = true;
            } else {
                z = false;
            }
        } else if (tryAddRanges(startId, endId, true)) {
            this.mRanges.add(new IntRange(startId, endId, client));
            z = true;
        } else {
            z = false;
        }
        return z;
    }

    public synchronized boolean disableRange(int startId, int endId, String client) {
        boolean z;
        int len = this.mRanges.size();
        int i;
        ArrayList<ClientRange> clients;
        int crLength;
        int crIndex;
        ClientRange cr;
        if (startId == 0 && endId == 999) {
            ArrayList<IntRange> mRangesTmp = new ArrayList(this.mRanges);
            i = 0;
            while (i < this.mRanges.size()) {
                clients = ((IntRange) this.mRanges.get(i)).mClients;
                crLength = clients.size();
                for (crIndex = 0; crIndex < crLength; crIndex++) {
                    cr = (ClientRange) clients.get(crIndex);
                    if (cr.mStartId >= startId && cr.mEndId <= endId && cr.mClient.equals(client)) {
                        this.mRanges.remove(i);
                        i--;
                        break;
                    }
                }
                i++;
            }
            if (tryAddRanges(startId, endId, false)) {
                z = true;
            } else {
                this.mRanges.removeAll(this.mRanges);
                this.mRanges.addAll(mRangesTmp);
                z = false;
            }
        } else {
            for (i = 0; i < len; i++) {
                IntRange range = (IntRange) this.mRanges.get(i);
                if (startId < range.mStartId) {
                    z = false;
                    break;
                }
                if (endId <= range.mEndId) {
                    clients = range.mClients;
                    crLength = clients.size();
                    if (crLength == 1) {
                        cr = (ClientRange) clients.get(0);
                        if (cr.mStartId == startId && cr.mEndId == endId && cr.mClient.equals(client)) {
                            this.mRanges.remove(i);
                            if (updateRanges()) {
                                z = true;
                            } else {
                                this.mRanges.add(i, range);
                                z = false;
                            }
                        } else {
                            z = false;
                        }
                    } else {
                        int largestEndId = Integer.MIN_VALUE;
                        boolean updateStarted = false;
                        crIndex = 0;
                        while (crIndex < crLength) {
                            cr = (ClientRange) clients.get(crIndex);
                            if (cr.mStartId != startId || cr.mEndId != endId || !cr.mClient.equals(client)) {
                                if (cr.mEndId > largestEndId) {
                                    largestEndId = cr.mEndId;
                                }
                                crIndex++;
                            } else if (crIndex != crLength - 1) {
                                IntRange intRange = new IntRange(range, crIndex);
                                if (crIndex == 0) {
                                    int nextStartId = ((ClientRange) clients.get(1)).mStartId;
                                    if (nextStartId != range.mStartId) {
                                        updateStarted = true;
                                        intRange.mStartId = nextStartId;
                                    }
                                    largestEndId = ((ClientRange) clients.get(1)).mEndId;
                                }
                                ArrayList<IntRange> newRanges = new ArrayList();
                                IntRange currentRange = intRange;
                                for (int nextIndex = crIndex + 1; nextIndex < crLength; nextIndex++) {
                                    ClientRange nextCr = (ClientRange) clients.get(nextIndex);
                                    if (nextCr.mStartId > largestEndId + 1) {
                                        updateStarted = true;
                                        currentRange.mEndId = largestEndId;
                                        newRanges.add(currentRange);
                                        currentRange = new IntRange(nextCr);
                                    } else {
                                        if (currentRange.mEndId < nextCr.mEndId) {
                                            currentRange.mEndId = nextCr.mEndId;
                                        }
                                        currentRange.mClients.add(nextCr);
                                    }
                                    if (nextCr.mEndId > largestEndId) {
                                        largestEndId = nextCr.mEndId;
                                    }
                                }
                                if (largestEndId < endId) {
                                    updateStarted = true;
                                    currentRange.mEndId = largestEndId;
                                }
                                newRanges.add(currentRange);
                                this.mRanges.remove(i);
                                this.mRanges.addAll(i, newRanges);
                                if (!updateStarted || updateRanges()) {
                                    z = true;
                                } else {
                                    this.mRanges.removeAll(newRanges);
                                    this.mRanges.add(i, range);
                                    z = false;
                                }
                            } else if (range.mEndId == largestEndId) {
                                clients.remove(crIndex);
                                z = true;
                            } else {
                                clients.remove(crIndex);
                                range.mEndId = largestEndId;
                                if (updateRanges()) {
                                    z = true;
                                } else {
                                    clients.add(crIndex, cr);
                                    range.mEndId = cr.mEndId;
                                    z = false;
                                }
                            }
                        }
                        continue;
                    }
                }
            }
            z = false;
        }
        return z;
    }

    public boolean updateRanges() {
        startUpdate();
        populateAllRanges();
        return finishUpdate();
    }

    protected boolean tryAddRanges(int startId, int endId, boolean selected) {
        startUpdate();
        populateAllRanges();
        addRange(startId, endId, selected);
        return finishUpdate();
    }

    public boolean isEmpty() {
        return this.mRanges.isEmpty();
    }

    private void populateAllRanges() {
        Iterator<IntRange> itr = this.mRanges.iterator();
        while (itr.hasNext()) {
            IntRange currRange = (IntRange) itr.next();
            addRange(currRange.mStartId, currRange.mEndId, true);
        }
    }

    private void populateAllClientRanges() {
        int len = this.mRanges.size();
        for (int i = 0; i < len; i++) {
            IntRange range = (IntRange) this.mRanges.get(i);
            int clientLen = range.mClients.size();
            for (int j = 0; j < clientLen; j++) {
                ClientRange nextRange = (ClientRange) range.mClients.get(j);
                addRange(nextRange.mStartId, nextRange.mEndId, true);
            }
        }
    }

    public void clearRanges() {
        this.mRanges.clear();
    }
}
