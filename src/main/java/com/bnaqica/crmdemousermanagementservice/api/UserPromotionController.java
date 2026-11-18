package com.bnaqica.crmdemousermanagementservice.api;

import com.bnaqica.crmdemousermanagementservice.dto.response.PromotionResponse;
import com.bnaqica.crmdemousermanagementservice.service.UserPromotionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** For the CRM: the {@code crm-promoter} client with scope {@code ums:promote}. Send {@code X-Correlation-Id}. */
@RestController
@RequiredArgsConstructor
public class UserPromotionController {

    private final UserPromotionService promotions;

    @PostMapping("/internal/users/{sub}/promote")
    PromotionResponse promote(@PathVariable UUID sub) {
        return promotions.promote(sub);
    }
}
