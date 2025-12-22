package com.etc.service;

import com.etc.entity.PassRecord;
import com.etc.repository.PassRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class QueryService {

    private final PassRecordRepository passRecordRepository;

    public Page<PassRecord> search(
            String plate,
            String checkpointId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String direction,
            int page,
            int size) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "gcsj"));
        return passRecordRepository.search(plate, checkpointId, startTime, endTime, direction, pageable);
    }

    public Optional<PassRecord> findById(Long id) {
        return passRecordRepository.findById(id);
    }

    public Long countToday() {
        return passRecordRepository.countToday();
    }

    public Long countTotal() {
        return passRecordRepository.count();
    }
}
