using System;
using System.Collections.Generic;
using JetBrains.Application.Parts;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Daemon.CodeInsights;
using JetBrains.ReSharper.Feature.Services.Protocol;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Resources.Shell;
using JetBrains.Rider.Model;

namespace ComponentInvestigation.Rider
{
    /// <summary>
    /// The "Relations" Code Vision lens shown next to "Usages"/"Inheritors" on a type. Clicking it
    /// computes all relations on the backend and pushes them to the frontend over the rd protocol.
    /// </summary>
    [SolutionComponent(Instantiation.DemandAnyThreadSafe)]
    public class RelationsCodeInsightsProvider : ICodeInsightsProvider
    {
        public const string Id = "ComponentInvestigation.Relations";

        private readonly RelationFinder _finder;

        public RelationsCodeInsightsProvider(RelationFinder finder)
        {
            _finder = finder;
        }

        public string ProviderId => Id;
        public string DisplayName => "Relations";
        public CodeVisionAnchorKind DefaultAnchor => CodeVisionAnchorKind.Default;
        public ICollection<CodeVisionRelativeOrdering> RelativeOrderings => Array.Empty<CodeVisionRelativeOrdering>();

        public bool IsAvailableIn(ISolution solution) => true;

        public void OnClick(CodeInsightHighlightInfo highlightInfo, ISolution solution, CodeInsightsClickInfo clickInfo)
        {
            if (highlightInfo.CodeInsightsHighlighting.DeclaredElement is not ITypeElement typeElement)
                return;

            RelationsResult result;
            using (ReadLockCookie.Create())
            {
                result = _finder.Find(typeElement);
            }

            solution.GetProtocolSolution().GetComponentInvestigationModel().ShowRelations.Fire(result);
        }

        public void OnExtraActionClick(CodeInsightHighlightInfo highlightInfo, string actionId, ISolution solution)
        {
        }
    }
}
