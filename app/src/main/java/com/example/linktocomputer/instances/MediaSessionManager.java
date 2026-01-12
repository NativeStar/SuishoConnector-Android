package com.example.linktocomputer.instances;

import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.util.Base64;

import androidx.annotation.Nullable;

import com.example.linktocomputer.Util;
import com.example.linktocomputer.service.ConnectMainService;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

public class MediaSessionManager extends MediaController.Callback {
    private final ConnectMainService networkService;
    private String notificationKey = "";
    private MediaSession.Token token;
    private MediaController mediaController;
    private MediaMetadata currentMetadata;
    private volatile String lastBitmapSha256 = "";
    private final Logger logger = LoggerFactory.getLogger(MediaSessionManager.class);

    private static class CalcBitmapResult {
        public CalcBitmapResult(byte[] bytes, String sha256) {
            this.bytes = bytes;
            this.sha256 = sha256;
        }
        public byte[] bytes;
        public String sha256;
    }

    public MediaSessionManager(ConnectMainService networkService, StatusBarNotification sbn, MediaSession.Token token) {
        this.networkService = networkService;
        setNewMediaSession(sbn, token);
    }

    public void setNewMediaSession(StatusBarNotification sbn, MediaSession.Token token) {
        String key = sbn.getKey();
        if(key.equals(notificationKey)) return;
        notificationKey = key;
        this.token = token;
        logger.debug("Set new media session");
        update();
    }

    public void appendControl(String action, @Nullable Long seekTime) {
        logger.debug("Append media session control:{}", action);
        switch (action) {
            case "changePlayState":
                PlaybackState state = mediaController.getPlaybackState();
                if(state != null) {
                    if(state.getState() == PlaybackState.STATE_PLAYING) {
                        mediaController.getTransportControls().pause();
                    } else {
                        mediaController.getTransportControls().play();
                    }
                }
                break;
            case "next":
                mediaController.getTransportControls().skipToNext();
                break;
            case "previous":
                mediaController.getTransportControls().skipToPrevious();
                break;
            case "seek":
                mediaController.getTransportControls().seekTo(Optional.ofNullable(seekTime).orElse(0L));
                break;
            default:
                logger.warn("Unknown control action:{}", action);
        }
    }

    private void update() {
        if(mediaController != null) {
            logger.debug("Session destroy.Clean media controller");
            mediaController.unregisterCallback(this);
        }
        logger.debug("Create new media controller");
        mediaController = new MediaController(this.networkService, token);
        new Handler(Looper.getMainLooper()).post(()->mediaController.registerCallback(this));
        this.onMetadataChanged(mediaController.getMetadata());
        PlaybackState state = mediaController.getPlaybackState();
        if(state != null)
            updatePlaybackState(state);
    }

    @Override
    public void onPlaybackStateChanged(@Nullable PlaybackState state) {
        super.onPlaybackStateChanged(state);
        if(state == null) return;
        updatePlaybackState(state);
    }

    @Override
    public void onSessionDestroyed() {
        super.onSessionDestroyed();
        logger.debug("Session destroyed");
        notificationKey = "";
        lastBitmapSha256 = "";
        currentMetadata = null;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("packetType", "updateMediaSessionPlaybackState");
        jsonObject.addProperty("hasSession", false);
        jsonObject.addProperty("playing", false);
        jsonObject.addProperty("position", 0);
        networkService.sendObject(jsonObject);
    }

    @Override
    public void onMetadataChanged(@Nullable MediaMetadata metadata) {
        if(metadata == null) return;
        if(currentMetadata != null && currentMetadata.equals(metadata)) {
            logger.debug("Metadata equals.Not change");
            return;
        }
        super.onMetadataChanged(metadata);
        currentMetadata = metadata;
        logger.debug("Update new metadata");
        new Thread(() -> {
            final String title = Optional.ofNullable(metadata.getString(MediaMetadata.METADATA_KEY_TITLE)).orElse("未知标题");
            final String artist = Optional.ofNullable(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)).orElse("未知艺术家");
            final String album = Optional.ofNullable(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)).orElse("未知专辑");
            final Bitmap artBitmap = Optional.ofNullable(metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)).orElse(metadata.getBitmap(MediaMetadata.METADATA_KEY_ART));
            final long duration = Optional.of(metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)).orElse(0L);
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("packetType", "updateMediaSessionMetadata");
            jsonObject.addProperty("title", title);
            jsonObject.addProperty("artist", artist);
            jsonObject.addProperty("album", album);
            jsonObject.addProperty("duration", duration / 1000);
            //图片
            if(artBitmap != null) {
                //是否与上次重复
                CalcBitmapResult currentBitmapSha256 = calcBitmapSha256(artBitmap);
                if(currentBitmapSha256.sha256.equals(lastBitmapSha256)) {
                    jsonObject.addProperty("image", "keep");
                    logger.debug("Image not change.Keep");
                } else {
                    lastBitmapSha256 = currentBitmapSha256.sha256;
                    jsonObject.addProperty("image", "data:image/jpeg;base64," + Base64.encodeToString(currentBitmapSha256.bytes, Base64.NO_WRAP));
                    logger.debug("Image change.Update");
                }
            } else {
                jsonObject.addProperty("image", "null");
                logger.debug("Image not change.Null");
            }
            networkService.sendObject(jsonObject);
        }).start();
    }

    private void updatePlaybackState(PlaybackState state) {
        int playState = state.getState();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("packetType", "updateMediaSessionPlaybackState");
        jsonObject.addProperty("hasSession", playState != PlaybackState.STATE_STOPPED && playState != PlaybackState.STATE_NONE && playState != PlaybackState.STATE_ERROR);
        jsonObject.addProperty("playing", playState == PlaybackState.STATE_PLAYING);
        jsonObject.addProperty("position", state.getPosition() / 1000);
        logger.debug("Send update playback state packet");
        networkService.sendObject(jsonObject);
    }

    private CalcBitmapResult calcBitmapSha256(Bitmap bitmap) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            return new CalcBitmapResult(stream.toByteArray(), Util.calculateSHA256(stream.toByteArray()));
        } catch (IOException e) {
            return new CalcBitmapResult(null, "");
        }
    }
}