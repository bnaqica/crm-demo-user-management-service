package com.bnaqica.crmdemousermanagementservice.exception;

/** Too many calls in the current minute. Answered with 429. */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("Too many promote calls; try again in a minute");
    }
}
