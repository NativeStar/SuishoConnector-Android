package com.example.linktocomputer.service;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.linktocomputer.IMediaProjectionServiceIPC;
import com.example.linktocomputer.R;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MediaProjectionService extends Service {
    private boolean readyExit = false;
    private Intent screenIntent;
    private AudioRecord audioRecord;
    private byte[] encryptKey;
    private byte[] encryptIv;
    private final IMediaProjectionServiceIPC.Stub mediaProjectionServiceIPC = new IMediaProjectionServiceIPC.Stub() {
        @Override
        public void run() {
            start();
        }

        //设置投屏intent
        @Override
        public void setScreenIntent(Intent data) throws RemoteException {
            screenIntent = data;
        }

        @Override
        public void setEncryptData(String keyBase64, String ivBase64) throws RemoteException {
            encryptKey = Base64.decode(keyBase64, Base64.DEFAULT);
            encryptIv = Base64.decode(ivBase64, Base64.DEFAULT);
        }

        //关闭进程
        @Override
        public void exit() throws RemoteException {
            Log.i("Media Projection Service", "Exit requested, shutting down gracefully");
            readyExit = true;
            try {
                if(audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                    Log.i("Media Projection Service", "AudioRecord stopped during exit");
                }
            } catch (Exception e) {
                Log.e("Media Projection Service", "Error during service exit", e);
            }
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                stopForeground(true);
                stopSelf();
                Process.killProcess(Process.myPid());
            }, 300);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mediaProjectionServiceIPC;
    }

    private void start() {
        Notification.Builder nBuilder = new Notification.Builder(getApplicationContext(), "MainServiceNotification");
        nBuilder.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getText(R.string.service_mediaProjection_notification_title))
                .setContentText(getText(R.string.service_mediaProjection_notification_content))
                .setWhen(System.currentTimeMillis())
                .setOngoing(true)
                .setAutoCancel(false)
                .setChannelId("foregroundService");
        startForeground(128, nBuilder.build());
        new Thread(() -> {
            MediaProjectionManager mediaProjectionManager = getSystemService(MediaProjectionManager.class);
            MediaProjection mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, screenIntent);
            if(mediaProjection == null) {
                Log.e("Media Projection Service", "Media Projection is null!!!");
                Process.killProcess(Process.myPid());
            }
            startAudioRecord(mediaProjection);
        }).start();
    }

    //编码部分是ai写的 玩不明白
    private void startAudioRecord(MediaProjection mediaProjection) {
        new Thread(() -> {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            DatagramSocket socket = null;
            MediaCodec mediaCodec = null;
            try {
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    Log.w("Media Projection Service", "No RECORD_AUDIO permission");
                    Process.killProcess(Process.myPid());
                    return;
                }
                AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .addMatchingUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build();
                final int sampleRate = 48000;
                final int channelCount = 2;
                final int bitRate = 196000;
                // 以较小的读取块降低采集链路延迟（20ms @ 48kHz, stereo, 16-bit -> 3840 bytes）
                final int readChunkSize = 3840;
                int minRecordBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                int recordBufferSize = Math.max(minRecordBufferSize, readChunkSize * 2);
                recordBufferSize = Math.max(recordBufferSize, 4096);
                AudioFormat audioFormat = new AudioFormat.Builder()
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build();
                audioRecord = new AudioRecord.Builder()
                        .setAudioFormat(audioFormat)
                        .setAudioPlaybackCaptureConfig(config)
                        .setBufferSizeInBytes(recordBufferSize)
                        .build();
                if(audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e("Media Projection Service", "AudioRecord initialization failed");
                    return;
                }
                socket = new DatagramSocket();
                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS);
                MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, channelCount);
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, readChunkSize);
                mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mediaCodec.start();
                final InetAddress targetAddress = InetAddress.getByName("192.168.3.10");
                audioRecord.startRecording();
                ByteBuffer audioBuffer = ByteBuffer.allocateDirect(readChunkSize);
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int seq = 0;
                final int headerSize = 4 + 4 + 8; // magic + seq + ptsUs
                final int magic = 0x41463031; // "AF01"
                final ByteOrder headerOrder = ByteOrder.BIG_ENDIAN;
                while (!readyExit) {
                    try {
                        audioBuffer.clear();
                        int readLength = audioRecord.read(audioBuffer, readChunkSize, AudioRecord.READ_BLOCKING);

                        if(readLength <= 0) {
                            Log.w("Media Projection Service", "AudioRecord read returned: " + readLength);
                            continue;
                        }
                        int inputBufferIndex = mediaCodec.dequeueInputBuffer(10000);
                        if(inputBufferIndex >= 0) {
                            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                            if(inputBuffer != null) {
                                inputBuffer.clear();
                                // 正确设置audioBuffer的读取范围
                                audioBuffer.position(0);
                                audioBuffer.limit(readLength);
                                inputBuffer.put(audioBuffer);
                                long presentationTime = System.nanoTime() / 1000;
                                mediaCodec.queueInputBuffer(inputBufferIndex, 0, readLength, presentationTime, 0);
                            }
                        } else {
                            Log.w("Media Projection Service", "No input buffer available: " + inputBufferIndex);
                            continue;
                        }
                        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                        while (outputBufferIndex >= 0) {
                            if((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                                continue;
                            }
                            if(bufferInfo.size > 0) {
                                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                                if(outputBuffer != null) {
                                    outputBuffer.position(bufferInfo.offset);
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                                    byte[] packetData = new byte[headerSize + bufferInfo.size];
                                    ByteBuffer header = ByteBuffer.wrap(packetData).order(headerOrder);
                                    header.putInt(magic);
                                    header.putInt(seq++);
                                    header.putLong(bufferInfo.presentationTimeUs);
                                    outputBuffer.get(packetData, headerSize, bufferInfo.size);
                                    DatagramPacket packet = new DatagramPacket(packetData, packetData.length, targetAddress, 8899);
                                    socket.send(packet);
                                }
                            } else {
                                Log.w("Media Projection Service", "Empty encoded output: " + bufferInfo.size + " bytes");
                            }
                            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                        }
//                        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                            Log.i("Media Projection Service", "Output format changed: " + mediaCodec.getOutputFormat());
//                        } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
//                        } else if (outputBufferIndex < 0) {
//                            Log.w("Media Projection Service", "Unexpected output buffer index: " + outputBufferIndex);
//                        }
                    } catch (Exception e) {
                        Log.e("Media Projection Service", "Error in encoding loop", e);
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e("Media Projection Service", "Fatal error in audio recording", e);
            } finally {
                try {
                    if(audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                    }
                    if(mediaCodec != null) {
                        mediaCodec.stop();
                        mediaCodec.release();
                    }
                    if(socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (Exception e) {
                    Log.e("Media Projection Service", "Error during cleanup", e);
                }
            }
        }, "AudioEncodingThread").start();
    }
}
