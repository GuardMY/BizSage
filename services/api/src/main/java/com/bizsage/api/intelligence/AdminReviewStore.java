package com.bizsage.api.intelligence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AdminReviewStore {

  private static final Logger log = LoggerFactory.getLogger(AdminReviewStore.class);

  private final AdminReviewMapper reviewMapper;

  public AdminReviewStore(AdminReviewMapper reviewMapper) {
    this.reviewMapper = reviewMapper;
  }

  public ReviewTicket createTicket(long intelligenceId, IntelligenceItem item) {
    ReviewTicket ticket = new ReviewTicket();
    ticket.setIntelligenceId(intelligenceId);
    ticket.setReviewStatus("PENDING");
    ticket.setSourceId("auto-collection");
    ticket.setWeight(1.0D);
    ticket.setRegionId(item.regionId() != null ? item.regionId() : "global");
    ticket.setIndustryId(item.industryId() != null ? item.industryId() : "global");
    reviewMapper.insert(ticket);
    log.debug("Created review ticket {} for intelligence {}", ticket.getId(), intelligenceId);
    return findTicket(ticket.id());
  }

  public int createTickets(List<IntelligenceItem> items) {
    int count = 0;
    for (IntelligenceItem item : items) {
      try {
        createTicket(item.id(), item);
        count++;
      } catch (Exception ex) {
        log.warn("Failed to create review ticket for intelligence {}: {}", item.id(), ex.getMessage());
      }
    }
    return count;
  }

  public List<ReviewTicket> listPending() {
    return reviewMapper.selectList(new LambdaQueryWrapper<ReviewTicket>()
        .eq(ReviewTicket::getReviewStatus, "PENDING")
        .orderByAsc(ReviewTicket::getId));
  }

  public List<ReviewTicket> listForIntelligence(long intelligenceId) {
    return reviewMapper.selectList(new LambdaQueryWrapper<ReviewTicket>()
        .eq(ReviewTicket::getIntelligenceId, intelligenceId)
        .orderByAsc(ReviewTicket::getId));
  }

  private ReviewTicket findTicket(long ticketId) {
    ReviewTicket ticket = reviewMapper.selectById(ticketId);
    if (ticket == null) {
      throw new IllegalArgumentException("review ticket not found");
    }
    return ticket;
  }
}
