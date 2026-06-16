using JetBrains.Application.BuildScript.Application.Zones;
using JetBrains.RdBackend.Common.Env;

namespace ComponentInvestigation.Rider
{
    // Binds this backend extension to the ReSharper host .NET feature zone so its components are
    // loaded inside the Rider backend.
    [ZoneMarker]
    public class ZoneMarker : IRequire<IReSharperHostNetFeatureZone>
    {
    }
}
