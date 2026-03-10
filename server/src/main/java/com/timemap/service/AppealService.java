package com.timemap.service;

import com.timemap.model.dto.*;

public interface AppealService {
    void submitAppeal(Long userId, AppealSubmitRequest request);
    AppealPageResponse getMyAppeals(Long userId, int page, int size);
    AppealPageResponse getAdminAppeals(Long adminUserId, Integer status, int page, int size);
    AppealResponse getAppealDetail(Long adminUserId, Long appealId);
    void resolveAppeal(Long adminUserId, HandleAppealRequest request);
    void rejectAppeal(Long adminUserId, HandleAppealRequest request);
    long getPendingCount();
}
