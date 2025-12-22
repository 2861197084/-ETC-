package com.etc.repository;

import com.etc.entity.Checkpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CheckpointRepository extends JpaRepository<Checkpoint, Long> {

    Optional<Checkpoint> findByCode(String code);

    List<Checkpoint> findByDistrict(String district);

    List<Checkpoint> findByStatus(Integer status);

    List<Checkpoint> findByType(String type);

    List<Checkpoint> findByNameContaining(String name);

    List<Checkpoint> findByDistrictContaining(String district);
}
