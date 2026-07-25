package com.wornux.audit;

import org.hibernate.envers.RevisionListener;

public class RevisionListenerImpl implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        Revision revision = (Revision) revisionEntity;

        revision.setModifierUser(CurrentUserUtils.currentUsername());
        revision.setIpAddress("0.0.0.0");
    }
}
