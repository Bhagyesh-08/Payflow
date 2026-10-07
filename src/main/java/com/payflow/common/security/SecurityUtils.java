package com.payflow.common.security;

import com.payflow.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Security Utilities.
 * 
 * Helper methods for extracting authenticated merchant information.
 */
public class SecurityUtils {

    /**
     * Gets authenticated merchant ID.
     * 
     * @return merchant ID from security context
     * @throws BusinessException if not authenticated
     */
    public static Long getAuthenticatedMerchantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("UNAUTHORIZED", "Not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof MerchantPrincipal)) {
            throw new BusinessException("UNAUTHORIZED", "Invalid authentication");
        }

        return ((MerchantPrincipal) principal).getMerchantId();
    }

    /**
     * Alias for getAuthenticatedMerchantId() for convenience.
     */
    public static Long getCurrentMerchantId() {
        return getAuthenticatedMerchantId();
    }

    /**
     * Validates that authenticated merchant matches resource owner.
     * 
     * @param resourceOwnerId merchant ID that owns the resource
     * @throws BusinessException if merchant mismatch
     */
    public static void validateMerchantAccess(Long resourceOwnerId) {
        Long authenticatedMerchantId = getAuthenticatedMerchantId();
        
        if (!authenticatedMerchantId.equals(resourceOwnerId)) {
            throw new BusinessException("FORBIDDEN", 
                "Cannot access resource owned by different merchant");
        }
    }
}
