package pl.kathelan.auth.exception;

import pl.kathelan.auth.api.dto.AccountStatus;

public class AccountNotEligibleException extends RuntimeException {

    public AccountNotEligibleException(String userId, AccountStatus status) {
        super("Account not eligible for authorization, userId=" + userId + ", status=" + status);
    }
}
