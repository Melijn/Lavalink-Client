/*
 * Copyright (c) 2017 Frederik Ar. Mikkelsen & NoobLance
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package lavalink.client.player;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import lavalink.client.LavalinkUtil;
import lavalink.client.io.LavalinkSocket;
import lavalink.client.io.Link;
import lavalink.client.io.filters.Filters;
import lavalink.client.player.event.IPlayerEventListener;
import lavalink.client.player.event.PlayerEvent;
import lavalink.client.player.event.PlayerPauseEvent;
import lavalink.client.player.event.PlayerResumeEvent;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LavalinkPlayer implements IPlayer {

    private AudioTrack track = null;
    private boolean paused = false;
    private long updateTime = -1;
    private long position = -1;
    private Filters filters = new Filters(this, () -> {
        //Do nothing :)
    });

    private final Link link;
    private List<IPlayerEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Constructor only for internal use
     *
     * @param link the parent link
     */
    public LavalinkPlayer(Link link) {
        this.link = link;
        addListener(new LavalinkInternalPlayerEventHandler());
    }

    /**
     * Invoked by {@link Link} to make sure we keep playing music on the new node
     * <p>
     * Used when we are moved to a new socket
     */
    public void onNodeChange() {
        AudioTrack track = getPlayingTrack();
        if (track != null) {
            track.setPosition(getTrackPosition());
            playTrack(track);
        }

    }

    @Override
    public AudioTrack getPlayingTrack() {
        return track;
    }

    @Override
    public void playTrack(AudioTrack track) {
        try {
            position = track.getPosition();
            TrackData trackData = track.getUserData(TrackData.class);

            DataObject json = DataObject.empty();
            json.put("op", "play");

            json.put("guildId", link.getGuildId());
            json.put("track", LavalinkUtil.toMessage(track));
            json.put("startTime", position);
            if (trackData != null) {
                json.put("startTime", trackData.startPos);
                json.put("endTime", trackData.endPos);
            }
            json.put("pause", paused);
            json.put("volume", filters.getVolume());
            //noinspection ConstantConditions
            link.getNode(true).send(json.toString());

            updateTime = System.currentTimeMillis();
            this.track = track;

            //TODO ("needs to be looked into")
            //emitEvent(new TrackStartEvent(this, track));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stopTrack() {
        track = null;

        LavalinkSocket node = link.getNode(false);
        if (node == null) return;
        DataObject json = DataObject.empty();
        json.put("op", "stop");
        json.put("guildId", link.getGuildId());
        node.send(json.toString());
    }

    @Override
    public void setPaused(boolean pause) {
        if (pause == paused) return;
        LavalinkSocket node = link.getNode(false);
        if (node != null) {
            DataObject json = DataObject.empty();
            json.put("op", "pause");
            json.put("guildId", link.getGuildId());
            json.put("pause", pause);
            node.send(json.toString());
        }
        paused = pause;

        if (pause) {
            emitEvent(new PlayerPauseEvent(this));
        } else {
            emitEvent(new PlayerResumeEvent(this));
        }
    }


    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public long getTrackPosition() {
        if (getPlayingTrack() == null) throw new IllegalStateException("Not currently playing anything");

        if (!paused) {
            // Account for the time since our last update
            long timeDiff = System.currentTimeMillis() - updateTime;
            return Math.min(position + timeDiff, track.getDuration());
        } else {
            return Math.min(position, track.getDuration());
        }

    }

    @Override
    public void seekTo(long position) {
        if (getPlayingTrack() == null) throw new IllegalStateException("Not currently playing anything");
        if (!getPlayingTrack().isSeekable()) throw new IllegalStateException("Track cannot be seeked");

        DataObject json =  DataObject.empty();
        json.put("op", "seek");
        json.put("guildId", link.getGuildId());
        json.put("position", position);
        //noinspection ConstantConditions
        link.getNode(true).send(json.toString());
    }

    @Override
    public void setVolume(int volume) {
        volume = Math.min(1000, Math.max(0, volume)); // Lavaplayer bounds
        filters.setVolume(volume);

        LavalinkSocket node = link.getNode(false);
        if (node == null) return;

        DataObject json = DataObject.empty();
        json.put("op", "volume");
        json.put("guildId", link.getGuildId());
        json.put("volume", volume);
        node.send(json.toString());
    }

    @Override
    public int getVolume() {
        return filters.getVolume();
    }

    @Override
    public void setSpeed(double speed) {
        filters.setSpeed(speed);

        LavalinkSocket node = link.getNode(false);
        if (node == null) return;

        DataObject json = DataObject.empty();
        json.put("op", "speed");
        json.put("guildId", link.getGuildId());
        json.put("speed", speed);
        node.send(json.toString());
    }

    @Override
    public double getSpeed() {
        return filters.getSpeed();
    }

    @Override
    public void setPitch(double pitch) {
        filters.setPitch(pitch);

        LavalinkSocket node = link.getNode(false);
        if (node == null) return;

        DataObject json = DataObject.empty();
        json.put("op", "pitch");
        json.put("guildId", link.getGuildId());
        json.put("pitch", pitch);
        node.send(json.toString());
    }

    @Override
    public double getPitch() {
        return filters.getPitch();
    }

    @Override
    public void setRate(double rate) {
        filters.setRate(rate);

        LavalinkSocket node = link.getNode(false);
        if (node == null) return;

        DataObject json = DataObject.empty();
        json.put("op", "rate");
        json.put("guildId", link.getGuildId());
        json.put("rate", rate);
        node.send(json.toString());
    }

    @Override
    public double getRate() {
        return filters.getRate();
    }

    @Override
    public void setTremolo(float frequency, float depth) {
        filters.setTremoloFrequency(frequency);
        filters.setTremoloDepth(depth);

        LavalinkSocket node = link.getNode(false);
        if (node == null) return;

        DataObject json = DataObject.empty();
        json.put("op", "tremolo");
        json.put("guildId", link.getGuildId());
        json.put("frequency", frequency);
        json.put("depth", depth);
        node.send(json.toString());
    }

    @Override
    public float getTremoloFrequency() {
        return filters.getTremoloFrequency();
    }

    @Override
    public float getTremoloDepth() {
        return filters.getTremoloDepth();
    }

    @Override
    public void setBand(int band, float gain) {
        filters.setBand(band, gain);

        LavalinkSocket node = link.getNode(false);
        if (node == null) return;

        float[] bands = filters.getBands();
        DataArray jsonBands = DataArray.empty();
        for (int i = 0; i < bands.length; i++) {
            jsonBands.add(
                DataObject.empty()
                    .put("band", i)
                    .put("gain", bands[i])
            );
        }

        DataObject json = DataObject.empty();
        json.put("op", "equalizer");
        json.put("guildId", link.getGuildId());
        json.put("bands", jsonBands);
        node.send(json.toString());
    }

    @Override
    public float[] getBands() {
        return filters.getBands();
    }

    public void provideState(DataObject json) {
        updateTime = json.getLong("time");
        position = json.getLong("position", 0);
    }

    @Override
    public void addListener(IPlayerEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(IPlayerEventListener listener) {
        listeners.remove(listener);
    }

    public void emitEvent(PlayerEvent event) {
        listeners.forEach(listener -> listener.onEvent(event));
    }

    void clearTrack() {
        track = null;
    }

    @SuppressWarnings("WeakerAccess")
    public Link getLink() {
        return link;
    }

}
