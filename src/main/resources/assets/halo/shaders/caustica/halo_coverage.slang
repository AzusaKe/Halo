module halo_coverage;
import caustica_coverage;
import caustica_types;
import halo_data;
namespace halo {
public struct HaloCoverage : ICoverageModel {
    public override float evaluateCoverage(CoverageInput input) {
        return coverage(ShaderDataPtr<Material>(input.instanceData)[0],hit(input.bindingData,input.primitiveIndex,input.barycentrics));
    }
};
}
