package com.etc.service;

import com.etc.entity.Checkpoint;
import com.etc.repository.CheckpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CheckpointService {

    private final CheckpointRepository checkpointRepository;

    public List<Checkpoint> findAll() {
        return checkpointRepository.findAll();
    }

    public Optional<Checkpoint> findById(Long id) {
        return checkpointRepository.findById(id);
    }

    public Optional<Checkpoint> findByCode(String code) {
        return checkpointRepository.findByCode(code);
    }

    public List<Checkpoint> findByDistrict(String district) {
        return checkpointRepository.findByDistrict(district);
    }

    public List<Checkpoint> findByStatus(Integer status) {
        return checkpointRepository.findByStatus(status);
    }
}
