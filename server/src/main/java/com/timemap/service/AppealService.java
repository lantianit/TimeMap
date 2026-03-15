package com.timemap.service;

import com.timemap.model.dto.*;
import com.timemap.model.vo.*;
public interface AppealService {
    void submitAppeal(Long userId, AppealSubmitRequest request);
    AppealPageVO getMyAppeals(Long userId, int page, int size);
    AppealPageVO getAdminAppeals(Long adminUserId, Integer status, int page, int size);
    AppealVO getAppealDetail(Long adminUserId, Long appealId);
    void resolveAppeal(Long adminUserId, HandleAppealRequest request);
    void rejectAppeal(Long adminUserId, HandleAppealRequest request);
    long getPendingCount();
}
