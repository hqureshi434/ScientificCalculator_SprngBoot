package com.example.scicalculator.audit;

import org.hibernate.envers.RevisionListener;

public class AuditRevisionListener implements RevisionListener {

    private static final String SYSTEM_USER = "system";

    @Override
    public void newRevision(Object revisionEntity) {
        AuditRevisionEntity revision = (AuditRevisionEntity) revisionEntity;
        String username = AuditUserContext.get();
        if (username == null || username.isBlank()) {
            username = SYSTEM_USER;
        }
        revision.setUsername(username);
    }
}
