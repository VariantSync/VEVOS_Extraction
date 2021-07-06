package de.variantsync.subjects.extraction.kh;

import de.ovgu.featureide.fm.core.base.IFeatureModel;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

public class FeatureModelConverter extends AnalysisComponent<IFeatureModel> {

    public FeatureModelConverter(@NonNull Configuration config) {
        super(config);
    }

    @Override
    protected void execute() {

    }

    @Override
    public @NonNull String getResultName() {
        return null;
    }
}