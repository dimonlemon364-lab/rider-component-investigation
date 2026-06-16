using System.Collections.Generic;
using JetBrains.ReSharper.Daemon.CodeInsights;
using JetBrains.ReSharper.Feature.Services.Daemon;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.Rider.Model;

namespace ComponentInvestigation.Rider
{
    /// <summary>
    /// Adds the "Relations" Code Vision lens onto every type declaration (C#/VB/F#) during analysis.
    /// </summary>
    [ElementProblemAnalyzer(new[] { typeof(ITypeDeclaration) },
        HighlightingTypes = new[] { typeof(CodeInsightsHighlighting) })]
    public class RelationsCodeInsightsAnalyzer : ElementProblemAnalyzer<ITypeDeclaration>
    {
        private readonly RelationsCodeInsightsProvider _provider;

        public RelationsCodeInsightsAnalyzer(RelationsCodeInsightsProvider provider)
        {
            _provider = provider;
        }

        protected override void Run(ITypeDeclaration element, ElementProblemAnalyzerData data, IHighlightingConsumer consumer)
        {
            var declaredElement = element.DeclaredElement;
            if (declaredElement == null)
                return;

            var range = element.GetNameDocumentRange();
            if (!range.IsValid())
                return;

            var highlighting = new CodeInsightsHighlighting(
                range,
                "Relations",
                "Show relations for this component/class",
                "Relations",
                _provider,
                declaredElement,
                null,
                new List<CodeVisionEntryExtraActionModel>());

            consumer.AddHighlighting(highlighting);
        }
    }
}
