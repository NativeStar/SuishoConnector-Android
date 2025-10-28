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
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.linktocomputer.IMediaProjectionServiceIPC;
import com.example.linktocomputer.R;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class MediaProjectionService extends Service {
    private boolean readyExit = false;
    private Intent screenIntent;
    private AudioRecord audioRecord;
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
            Process.killProcess(Process.myPid());
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mediaProjectionServiceIPC;
    }

    private void start() {
        Log.i("Media Projection Service", "Call start");
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
        Log.i("Media Projection Service", "Start record");
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
                
                int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
                minBufferSize = Math.max(minBufferSize, 4096);

                AudioFormat audioFormat = new AudioFormat.Builder()
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build();
                        
                audioRecord = new AudioRecord.Builder()
                        .setAudioFormat(audioFormat)
                        .setAudioPlaybackCaptureConfig(config)
                        .setBufferSizeInBytes(minBufferSize * 2)
                        .build();
                
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e("Media Projection Service", "AudioRecord initialization failed");
                    return;
                }
                
                socket = new DatagramSocket();
                
                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS);
                MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, channelCount);
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize);
                
                Log.i("Media Projection Service", "Configuring MediaCodec with Opus format");
                mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mediaCodec.start();
                
                Log.i("Media Projection Service", "MediaCodec started successfully");
                
                final InetAddress targetAddress = InetAddress.getByName("192.168.3.10");
                audioRecord.startRecording();
                
                Log.i("Media Projection Service", "AudioRecord started, beginning encoding loop");
                
                ByteBuffer audioBuffer = ByteBuffer.allocateDirect(minBufferSize);
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                while (!readyExit) {
                    try {
                        audioBuffer.clear();
                        int readLength = audioRecord.read(audioBuffer, minBufferSize, AudioRecord.READ_BLOCKING);
                        
                        if (readLength <= 0) {
                            Log.w("Media Projection Service", "AudioRecord read returned: " + readLength);
                            continue;
                        }
                        
                        int inputBufferIndex = mediaCodec.dequeueInputBuffer(10000);
                        if (inputBufferIndex >= 0) {
                            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                            if (inputBuffer != null) {
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
                            if (bufferInfo.size > 0) {
                                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                                if (outputBuffer != null) {
                                    byte[] encodedData = new byte[bufferInfo.size];
                                    outputBuffer.get(encodedData);
                                    
                                    for (int offset = 0; offset < bufferInfo.size; offset += 1024) {
                                        int length = Math.min(1024, bufferInfo.size - offset);
                                        DatagramPacket packet = new DatagramPacket(encodedData, offset, length, targetAddress, 8899);
                                        socket.send(packet);
                                    }
                                }
                            } else {
                                Log.w("Media Projection Service", "Empty encoded output: " + bufferInfo.size + " bytes");
                            }
                            
                            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                        }
                        
                        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            Log.i("Media Projection Service", "Output format changed: " + mediaCodec.getOutputFormat());
                        } else if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        } else if (outputBufferIndex < 0) {
                            Log.w("Media Projection Service", "Unexpected output buffer index: " + outputBufferIndex);
                        }
                    } catch (Exception e) {
                        Log.e("Media Projection Service", "Error in encoding loop", e);
                        break;
                    }
                }
                
            } catch (Exception e) {
                Log.e("Media Projection Service", "Fatal error in audio recording", e);
            } finally {
                try {
                    if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                        Log.i("Media Projection Service", "AudioRecord stopped");
                    }
                    if (mediaCodec != null) {
                        mediaCodec.stop();
                        mediaCodec.release();
                        Log.i("Media Projection Service", "MediaCodec stopped and released");
                    }
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                        Log.i("Media Projection Service", "DatagramSocket closed");
                    }
                } catch (Exception e) {
                    Log.e("Media Projection Service", "Error during cleanup", e);
                }
            }
        }, "AudioEncodingThread").start();
    }
}