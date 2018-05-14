package com.google.android.mms.util;

import android.content.Context;
import android.drm.DrmConvertedStatus;
import android.drm.DrmManagerClient;
import android.util.Log;
import com.google.android.mms.pdu.PduPart;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class DrmConvertSession {
    private static final String TAG = "DrmConvertSession";
    private int mConvertSessionId;
    private DrmManagerClient mDrmClient;

    private DrmConvertSession(DrmManagerClient drmClient, int convertSessionId) {
        this.mDrmClient = drmClient;
        this.mConvertSessionId = convertSessionId;
    }

    public static DrmConvertSession open(Context context, String mimeType) {
        DrmManagerClient drmClient = null;
        int convertSessionId = -1;
        if (!(context == null || mimeType == null || mimeType.equals(""))) {
            try {
                DrmManagerClient drmClient2 = new DrmManagerClient(context);
                try {
                    convertSessionId = drmClient2.openConvertSession(mimeType);
                } catch (IllegalArgumentException e) {
                    try {
                        Log.w(TAG, "Conversion of Mimetype: " + mimeType + " is not supported.", e);
                    } catch (IllegalArgumentException e2) {
                        drmClient = drmClient2;
                        Log.w(TAG, "DrmManagerClient instance could not be created, context is Illegal.");
                        if (drmClient != null) {
                        }
                        return null;
                    } catch (IllegalStateException e3) {
                        drmClient = drmClient2;
                        Log.w(TAG, "DrmManagerClient didn't initialize properly.");
                        if (drmClient != null) {
                        }
                        return null;
                    }
                } catch (IllegalStateException e4) {
                    Log.w(TAG, "Could not access Open DrmFramework.", e4);
                }
                drmClient = drmClient2;
            } catch (IllegalArgumentException e5) {
                Log.w(TAG, "DrmManagerClient instance could not be created, context is Illegal.");
                if (drmClient != null) {
                }
                return null;
            } catch (IllegalStateException e6) {
                Log.w(TAG, "DrmManagerClient didn't initialize properly.");
                if (drmClient != null) {
                }
                return null;
            }
        }
        if (drmClient != null || convertSessionId < 0) {
            return null;
        }
        return new DrmConvertSession(drmClient, convertSessionId);
    }

    public byte[] convert(byte[] inBuffer, int size) {
        if (inBuffer != null) {
            try {
                DrmConvertedStatus convertedStatus;
                if (size != inBuffer.length) {
                    byte[] buf = new byte[size];
                    System.arraycopy(inBuffer, 0, buf, 0, size);
                    convertedStatus = this.mDrmClient.convertData(this.mConvertSessionId, buf);
                } else {
                    convertedStatus = this.mDrmClient.convertData(this.mConvertSessionId, inBuffer);
                }
                if (convertedStatus == null || convertedStatus.statusCode != 1 || convertedStatus.convertedData == null) {
                    return null;
                }
                return convertedStatus.convertedData;
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Buffer with data to convert is illegal. Convertsession: " + this.mConvertSessionId, e);
                return null;
            } catch (IllegalStateException e2) {
                Log.w(TAG, "Could not convert data. Convertsession: " + this.mConvertSessionId, e2);
                return null;
            }
        }
        throw new IllegalArgumentException("Parameter inBuffer is null");
    }

    public int close(String filename) {
        IOException e;
        FileNotFoundException e2;
        Throwable th;
        IllegalArgumentException e3;
        SecurityException e4;
        int result = 491;
        if (this.mDrmClient == null || this.mConvertSessionId < 0) {
            return 491;
        }
        try {
            DrmConvertedStatus convertedStatus = this.mDrmClient.closeConvertSession(this.mConvertSessionId);
            if (convertedStatus == null || convertedStatus.statusCode != 1 || convertedStatus.convertedData == null) {
                return 406;
            }
            RandomAccessFile rndAccessFile = null;
            try {
                RandomAccessFile rndAccessFile2 = new RandomAccessFile(filename, "rw");
                try {
                    rndAccessFile2.seek((long) convertedStatus.offset);
                    rndAccessFile2.write(convertedStatus.convertedData);
                    if (rndAccessFile2 == null) {
                        return PduPart.P_CONTENT_TRANSFER_ENCODING;
                    }
                    try {
                        rndAccessFile2.close();
                        return PduPart.P_CONTENT_TRANSFER_ENCODING;
                    } catch (IOException e5) {
                        result = 492;
                        Log.w(TAG, "Failed to close File:" + filename + ".", e5);
                        return 492;
                    }
                } catch (FileNotFoundException e6) {
                    e2 = e6;
                    rndAccessFile = rndAccessFile2;
                    result = 492;
                    try {
                        Log.w(TAG, "File: " + filename + " could not be found.", e2);
                        if (rndAccessFile != null) {
                            return 492;
                        }
                        try {
                            rndAccessFile.close();
                            return 492;
                        } catch (IOException e52) {
                            result = 492;
                            Log.w(TAG, "Failed to close File:" + filename + ".", e52);
                            return 492;
                        }
                    } catch (Throwable th2) {
                        th = th2;
                        if (rndAccessFile != null) {
                            try {
                                rndAccessFile.close();
                            } catch (IOException e522) {
                                result = 492;
                                Log.w(TAG, "Failed to close File:" + filename + ".", e522);
                            }
                        }
                        throw th;
                    }
                } catch (IOException e7) {
                    e522 = e7;
                    rndAccessFile = rndAccessFile2;
                    result = 492;
                    Log.w(TAG, "Could not access File: " + filename + " .", e522);
                    if (rndAccessFile != null) {
                        return 492;
                    }
                    try {
                        rndAccessFile.close();
                        return 492;
                    } catch (IOException e5222) {
                        result = 492;
                        Log.w(TAG, "Failed to close File:" + filename + ".", e5222);
                        return 492;
                    }
                } catch (IllegalArgumentException e8) {
                    e3 = e8;
                    rndAccessFile = rndAccessFile2;
                    result = 492;
                    Log.w(TAG, "Could not open file in mode: rw", e3);
                    if (rndAccessFile != null) {
                        return 492;
                    }
                    try {
                        rndAccessFile.close();
                        return 492;
                    } catch (IOException e52222) {
                        result = 492;
                        Log.w(TAG, "Failed to close File:" + filename + ".", e52222);
                        return 492;
                    }
                } catch (SecurityException e9) {
                    e4 = e9;
                    rndAccessFile = rndAccessFile2;
                    Log.w(TAG, "Access to File: " + filename + " was denied denied by SecurityManager.", e4);
                    if (rndAccessFile != null) {
                        return 491;
                    }
                    try {
                        rndAccessFile.close();
                        return 491;
                    } catch (IOException e522222) {
                        result = 492;
                        Log.w(TAG, "Failed to close File:" + filename + ".", e522222);
                        return 492;
                    }
                } catch (Throwable th3) {
                    th = th3;
                    rndAccessFile = rndAccessFile2;
                    if (rndAccessFile != null) {
                        rndAccessFile.close();
                    }
                    throw th;
                }
            } catch (FileNotFoundException e10) {
                e2 = e10;
                result = 492;
                Log.w(TAG, "File: " + filename + " could not be found.", e2);
                if (rndAccessFile != null) {
                    return 492;
                }
                rndAccessFile.close();
                return 492;
            } catch (IOException e11) {
                e522222 = e11;
                result = 492;
                Log.w(TAG, "Could not access File: " + filename + " .", e522222);
                if (rndAccessFile != null) {
                    return 492;
                }
                rndAccessFile.close();
                return 492;
            } catch (IllegalArgumentException e12) {
                e3 = e12;
                result = 492;
                Log.w(TAG, "Could not open file in mode: rw", e3);
                if (rndAccessFile != null) {
                    return 492;
                }
                rndAccessFile.close();
                return 492;
            } catch (SecurityException e13) {
                e4 = e13;
                Log.w(TAG, "Access to File: " + filename + " was denied denied by SecurityManager.", e4);
                if (rndAccessFile != null) {
                    return 491;
                }
                rndAccessFile.close();
                return 491;
            }
        } catch (IllegalStateException e14) {
            Log.w(TAG, "Could not close convertsession. Convertsession: " + this.mConvertSessionId, e14);
            return result;
        }
    }
}
