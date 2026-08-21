package com.hirewise.be.authorization;

/**
 * Permission code constants (RBAC Design section 2.2) - lets callers use
 * compile-time-checked constants instead of hand-typed strings when calling
 * {@code AccessControlService.checkAccess(...)} / {@code @RequiresOwnership}.
 * The source of truth for which role is granted which permission remains the
 * {@code role_permissions} table in the DB (see
 * {@code V2__create_roles_and_permissions.sql}) - it is NOT duplicated here.
 */
public final class PermissionCodes {

    private PermissionCodes() {
    }

    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_VIEW = "USER_VIEW";
    public static final String ROLE_ASSIGN = "ROLE_ASSIGN";
    public static final String PIPELINE_MANAGE = "PIPELINE_MANAGE";
    public static final String EMAIL_TEMPLATE_MANAGE = "EMAIL_TEMPLATE_MANAGE";
    public static final String INTEGRATION_MANAGE = "INTEGRATION_MANAGE";

    public static final String JOB_CREATE = "JOB_CREATE";
    public static final String JOB_EDIT = "JOB_EDIT";
    public static final String JOB_SUBMIT = "JOB_SUBMIT";
    public static final String JOB_APPROVE = "JOB_APPROVE";
    public static final String JOB_VIEW = "JOB_VIEW";
    public static final String JOB_PUBLISH = "JOB_PUBLISH";

    public static final String APPLICATION_VIEW = "APPLICATION_VIEW";
    public static final String APPLICATION_MOVE_STAGE = "APPLICATION_MOVE_STAGE";
    public static final String APPLICATION_REJECT = "APPLICATION_REJECT";

    public static final String AI_VIEW = "AI_VIEW";

    public static final String INTERVIEW_SCHEDULE = "INTERVIEW_SCHEDULE";
    public static final String INTERVIEW_BOOK = "INTERVIEW_BOOK";

    public static final String SCORECARD_TEMPLATE_MANAGE = "SCORECARD_TEMPLATE_MANAGE";
    public static final String SCORECARD_SUBMIT = "SCORECARD_SUBMIT";

    public static final String OFFER_CREATE = "OFFER_CREATE";
    public static final String OFFER_SEND = "OFFER_SEND";
    public static final String OFFER_SIGN = "OFFER_SIGN";

    public static final String SLA_CONFIGURE = "SLA_CONFIGURE";
    public static final String SLA_VIEW_ALERT = "SLA_VIEW_ALERT";

    public static final String REPORT_VIEW = "REPORT_VIEW";

    public static final String CANDIDATE_APPLY = "CANDIDATE_APPLY";
    public static final String CANDIDATE_PROFILE_VIEW = "CANDIDATE_PROFILE_VIEW";
}
