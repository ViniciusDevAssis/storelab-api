package com.viniciusdevassis.storelab.migration;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MigrationRunner {
    private static final Logger LOG = Logger.getLogger(MigrationRunner.class);
    @Inject V001AddActiveToExistingProducts v001;
    void onStart(@Observes StartupEvent event) {
        try { v001.run(); LOG.info("Firestore data migrations are up to date"); }
        catch (Exception e) { throw new IllegalStateException("Could not apply Firestore data migrations", e); }
    }
}
