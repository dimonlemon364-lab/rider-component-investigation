using System.Collections.Generic;
using JetBrains.Application.Parts;
using JetBrains.Application.Progress;
using JetBrains.DocumentModel;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.CSharp.Tree;
using JetBrains.ReSharper.Psi.Search;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.Rider.Model;
using JetBrains.Util;
using IReference = JetBrains.ReSharper.Psi.Resolve.IReference;

namespace ComponentInvestigation.Rider
{
    /// <summary>
    /// Enumerates a type + its members and turns every usage into a <see cref="RelationEntry"/>.
    /// </summary>
    [SolutionComponent(Instantiation.DemandAnyThreadSafe)]
    public class RelationFinder
    {
        private const int MaxSnippetLength = 400;

        private readonly ISolution _solution;

        public RelationFinder(ISolution solution)
        {
            _solution = solution;
        }

        public RelationsResult Find(ITypeElement typeElement)
        {
            var entries = new List<RelationEntry>();

            var finder = _solution.GetPsiServices().Finder;
            var searchDomain = SearchDomainFactory.Instance.CreateSearchDomain(_solution, false);

            // The type itself (instantiation, base type, fields/params of the type, generics).
            CollectFor(typeElement, MemberKind.Class, finder, searchDomain, entries);

            // Each member.
            foreach (var member in typeElement.GetMembers())
                CollectFor(member, KindOf(member), finder, searchDomain, entries);

            return new RelationsResult(typeElement.ShortName, entries);
        }

        private void CollectFor(IDeclaredElement element, MemberKind kind, IFinder finder,
            ISearchDomain searchDomain, List<RelationEntry> entries)
        {
            var references = finder.FindReferences(element, searchDomain, NullProgressIndicator.Create());
            foreach (var reference in references)
            {
                try
                {
                    var entry = ToEntry(reference, element, kind);
                    if (entry != null)
                        entries.Add(entry);
                }
                catch
                {
                    // Skip a reference we can't map (e.g. generated / secondary PSI).
                }
            }
        }

        private RelationEntry ToEntry(IReference reference, IDeclaredElement element, MemberKind kind)
        {
            var node = reference.GetTreeNode();
            if (node == null)
                return null;

            var sourceFile = node.GetSourceFile();
            if (sourceFile == null)
                return null;

            var docRange = node.GetDocumentRange();
            if (!docRange.IsValid() || docRange.Document == null)
                return null;

            var document = docRange.Document;
            var startOffset = docRange.TextRange.StartOffset;
            var coords = document.GetCoordsByOffset(startOffset);

            var location = sourceFile.GetLocation();
            string relative;
            try
            {
                relative = location.MakeRelativeTo(_solution.SolutionDirectory).ToString().Replace('\\', '/');
            }
            catch
            {
                relative = location.FullPath;
            }

            return new RelationEntry(
                memberName: element.ShortName,
                memberKind: kind,
                filePath: location.FullPath,
                relativePath: relative,
                offset: startOffset,
                length: docRange.TextRange.Length,
                line: (int)coords.Line + 1,
                containerName: GetContainerName(node),
                accessKind: AccessClassifier.Classify(reference, element),
                previewText: GetLineText(document, coords).Trim(),
                usageSnippet: GetSnippet(node));
        }

        private static MemberKind KindOf(ITypeMember member) => member switch
        {
            IMethod => MemberKind.Method,
            IProperty => MemberKind.Property,
            IField { IsConstant: true } => MemberKind.Constant,
            IField => MemberKind.Field,
            IEvent => MemberKind.Event,
            _ => MemberKind.Other
        };

        private static string GetContainerName(ITreeNode node)
        {
            var declaration = node.GetContainingNode<IDeclaration>();
            return declaration?.DeclaredName ?? "";
        }

        private static string GetSnippet(ITreeNode node)
        {
            ITreeNode container = node.GetContainingNode<ICSharpStatement>(true) ?? node;
            var text = container.GetText().Trim();
            return text.Length > MaxSnippetLength ? text.Substring(0, MaxSnippetLength) + " …" : text;
        }

        private static string GetLineText(IDocument document, DocumentCoords coords)
        {
            var lineStart = document.GetLineStartOffset(coords.Line);
            var lineEnd = document.GetLineEndOffsetNoLineBreak(coords.Line);
            return document.GetText(new TextRange(lineStart, lineEnd));
        }
    }
}
