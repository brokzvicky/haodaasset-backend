package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.AttendanceDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceDeviceRepository extends JpaRepository<AttendanceDevice, Long> {

    Optional<AttendanceDevice> findBySerialNumber(String serialNumber);

    List<AttendanceDevice> findAllByOrderByLastSeenAtDesc();
}
