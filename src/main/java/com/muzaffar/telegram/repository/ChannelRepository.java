package com.muzaffar.telegram.repository;

import com.muzaffar.telegram.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {
    
    Optional<Channel> findByChannelId(String channelId);
    
    Optional<Channel> findByChannelUsername(String channelUsername);
    
    @Query("SELECT c FROM Channel c WHERE c.isActive = true")
    List<Channel> findAllActiveChannels();
    
    boolean existsByChannelId(String channelId);
}
