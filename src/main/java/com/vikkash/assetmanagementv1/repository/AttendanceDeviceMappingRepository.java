package com.vikkash.assetmanagementv1.repository;

import com.vikkash.assetmanagementv1.entity.AttendanceDeviceMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceDeviceMappingRepository extends JpaRepository<AttendanceDeviceMapping, Long> {

    Optional<AttendanceDeviceMapping> findByDevicePin(String devicePin);

    Optional<AttendanceDeviceMapping> findByEmployeeId(String employeeId);

    boolean existsByDevicePin(String devicePin);

    List<AttendanceDeviceMapping> findAllByOrderByDevicePinAsc();
}
