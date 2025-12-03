package com.muzaffar.telegram.service;

import com.muzaffar.telegram.entity.Channel;
import com.muzaffar.telegram.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelService {
    
    private final ChannelRepository channelRepository;
    
    /**
     * Get all active channels
     */
    public List<Channel> getAllActiveChannels() {
        return channelRepository.findAllActiveChannels();
    }
    
    /**
     * Get all channels (including inactive)
     */
    public List<Channel> getAllChannels() {
        return channelRepository.findAll();
    }
    
    /**
     * Find channel by ID
     */
    public Optional<Channel> findByChannelId(String channelId) {
        return channelRepository.findByChannelId(channelId);
    }
    
    /**
     * Find channel by username
     */
    public Optional<Channel> findByChannelUsername(String channelUsername) {
        return channelRepository.findByChannelUsername(channelUsername);
    }
    
    /**
     * Add a new mandatory channel
     */
    @Transactional
    public Channel addChannel(String channelId, String channelName, String channelUsername) {
        if (channelRepository.existsByChannelId(channelId)) {
            throw new IllegalArgumentException("Channel already exists with ID: " + channelId);
        }
        
        Channel channel = Channel.builder()
            .channelId(channelId)
            .channelName(channelName)
            .channelUsername(channelUsername)
            .isActive(true)
            .build();
        
        Channel saved = channelRepository.save(channel);
        log.info("Added new mandatory channel: {} ({})", channelName, channelId);
        return saved;
    }
    
    /**
     * Remove a channel (soft delete by setting inactive)
     */
    @Transactional
    public boolean removeChannel(String channelId) {
        Optional<Channel> channelOpt = channelRepository.findByChannelId(channelId);
        if (channelOpt.isEmpty()) {
            return false;
        }
        
        Channel channel = channelOpt.get();
        channel.setIsActive(false);
        channelRepository.save(channel);
        log.info("Removed mandatory channel: {} ({})", channel.getChannelName(), channelId);
        return true;
    }
    
    /**
     * Permanently delete a channel
     */
    @Transactional
    public boolean deleteChannel(String channelId) {
        Optional<Channel> channelOpt = channelRepository.findByChannelId(channelId);
        if (channelOpt.isEmpty()) {
            return false;
        }
        
        channelRepository.delete(channelOpt.get());
        log.info("Permanently deleted channel: {}", channelId);
        return true;
    }
    
    /**
     * Reactivate a channel
     */
    @Transactional
    public boolean reactivateChannel(String channelId) {
        Optional<Channel> channelOpt = channelRepository.findByChannelId(channelId);
        if (channelOpt.isEmpty()) {
            return false;
        }
        
        Channel channel = channelOpt.get();
        channel.setIsActive(true);
        channelRepository.save(channel);
        log.info("Reactivated channel: {} ({})", channel.getChannelName(), channelId);
        return true;
    }
}
