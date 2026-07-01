package com.offertrack.applications.cache;

import com.offertrack.applications.dto.ApplicationDraftResponse;
import java.util.Optional;

public interface AiDraftCacheService {
  Optional<ApplicationDraftResponse> get(String key);

  void put(String key, ApplicationDraftResponse draft);
}
