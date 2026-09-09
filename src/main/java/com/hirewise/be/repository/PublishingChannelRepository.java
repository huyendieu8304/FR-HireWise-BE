package com.hirewise.be.repository;

import com.hirewise.be.domain.PublishingChannel;
import com.hirewise.be.domain.PublishingChannelCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link PublishingChannel} entities (UC-19).
 */
public interface PublishingChannelRepository extends JpaRepository<PublishingChannel, Long> {

    /**
     * UC-19: every channel, including the disabled ones - the settings screen
     * has to show a switch for each.
     *
     * @return all channels in the order HR Admin sees them
     */
    List<PublishingChannel> findAllByOrderByDisplayOrderAsc();

    /**
     * UC-31 EX-01: only the channels HR Admin has switched on are offered in
     * the share modal.
     *
     * @return the enabled channels, in display order
     */
    List<PublishingChannel> findByEnabledTrueOrderByDisplayOrderAsc();

    /**
     * @param code the channel code from the URL path
     * @return the channel, if it is configured
     */
    Optional<PublishingChannel> findByCode(PublishingChannelCode code);
}
