package vn.ai_study_hub_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.ai_study_hub_api.model.InvoiceEntity;

import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT i FROM InvoiceEntity i WHERE i.id = :id")
    java.util.Optional<InvoiceEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") java.util.UUID id);
}
