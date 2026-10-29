using System.Collections.Generic;
using JetBrains.Application.Parts;
using JetBrains.Application.Progress;
using JetBrains.Diagnostics;
using JetBrains.DocumentModel;
using JetBrains.ProjectModel;
using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.CSharp.Tree;
using JetBrains.ReSharper.Psi.Search;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.Rider.Model;
using JetBrains.Util;
using JetBrains.Util.Logging;
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

        // TEMPORARY diagnostics: confirm whether ASP/WebForms markup references are found by the
        // finder and, if so, why ToEntry was dropping them. Downgrade/remove once the WebForms
        // markup-usage issue is verified fixed.
        private static readonly ILogger ourLogger = Logger.GetLogger(typeof(RelationFinder));

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

            // The file(s) where the class itself is declared. Usages inside these are the class's own
            // internal self-references (it reads its own fields, calls its own methods) — we only want
            // external usage, so they are excluded. GetDeclarations() covers all partial-class files.
            var declaringFiles = new HashSet<IPsiSourceFile>();
            foreach (var declaration in typeElement.GetDeclarations())
            {
                var sourceFile = declaration.GetSourceFile();
                if (sourceFile != null)
                    declaringFiles.Add(sourceFile);
            }

            // The type itself (instantiation, base type, fields/params of the type, generics).
            CollectFor(typeElement, MemberKind.Class, finder, searchDomain, declaringFiles, entries);

            // Each member. Constructors are skipped: every instantiation (new Foo()) is already
            // collected above under the type itself (MemberKind.Class). Enumerating the constructor
            // separately would re-report those same call sites as a duplicate "Other: .ctor" group.
            foreach (var member in typeElement.GetMembers())
            {
                if (member is IConstructor)
                    continue;
                CollectFor(member, KindOf(member), finder, searchDomain, declaringFiles, entries);
            }

            return new RelationsResult(typeElement.ShortName, entries);
        }

        private void CollectFor(IDeclaredElement element, MemberKind kind, IFinder finder,
            ISearchDomain searchDomain, ISet<IPsiSourceFile> excludedFiles, List<RelationEntry> entries)
        {
            var references = finder.FindReferences(element, searchDomain, NullProgressIndicator.Create());
            ourLogger.Verbose("RelationFinder: '{0}' ({1}) -> {2} reference(s) found",
                element.ShortName, kind, references.Length);

            foreach (var reference in references)
            {
                try
                {
                    var entry = ToEntry(reference, element, kind, excludedFiles);
                    if (entry != null)
                        entries.Add(entry);
                }
                catch (System.Exception e)
                {
                    // Skip a reference we can't map (e.g. generated / secondary PSI), but record it.
                    ourLogger.Warn("RelationFinder: dropped reference to '{0}' (exception {1}: {2}), node={3}",
                        element.ShortName, e.GetType().Name, e.Message,
                        SafeNodeType(reference));
                }
            }
        }

        private static string SafeNodeType(IReference reference)
        {
            try { return reference.GetTreeNode()?.GetType().Name ?? "<null>"; }
            catch { return "<unavailable>"; }
        }

        private RelationEntry ToEntry(IReference reference, IDeclaredElement element, MemberKind kind,
            ISet<IPsiSourceFile> excludedFiles)
        {
            var node = reference.GetTreeNode();
            if (node == null)
            {
                ourLogger.Warn("RelationFinder: dropped reference to '{0}' (tree node null)", element.ShortName);
                return null;
            }

            var sourceFile = node.GetSourceFile();
            if (sourceFile == null)
            {
                ourLogger.Warn("RelationFinder: dropped reference to '{0}' (source file null), node={1}",
                    element.ShortName, node.GetType().Name);
                return null;
            }

            if (excludedFiles.Contains(sourceFile))
                return null;

            // ASP/WebForms (and other secondary-PSI) references can report an invalid range on the
            // tree node itself; fall back to the node's navigation range before giving up. Even when no
            // range resolves we still emit the usage (best-effort line 1) so it appears in the list.
            var docRange = node.GetDocumentRange();
            if (!docRange.IsValid() || docRange.Document == null)
                docRange = node.GetNavigationRange();

            var hasRange = docRange.IsValid() && docRange.Document != null;
            var document = hasRange ? docRange.Document : null;
            var startOffset = hasRange ? docRange.TextRange.StartOffset : 0;
            var length = hasRange ? docRange.TextRange.Length : 0;

            int line;
            string previewText;
            if (document != null)
            {
                var coords = SafeCoords(document, startOffset);
                line = (int)coords.Line + 1;
                previewText = GetLineText(document, coords).Trim();
            }
            else
            {
                ourLogger.Warn("RelationFinder: no valid range for reference to '{0}' in '{1}' (node={2}); emitting at line 1",
                    element.ShortName, sourceFile.Name, node.GetType().Name);
                line = 1;
                previewText = node.GetText().Trim();
            }

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
                length: length,
                line: line,
                containerName: GetContainerName(node),
                accessKind: AccessClassifier.Classify(reference, element),
                previewText: previewText,
                usageSnippet: GetSnippet(node));
        }

        /// <summary>Offset → line/column, degrading to the document start (line 1) when the offset is bad.</summary>
        private static DocumentCoords SafeCoords(IDocument document, int offset)
        {
            try
            {
                return document.GetCoordsByOffset(offset);
            }
            catch
            {
                return document.GetCoordsByOffset(0);
            }
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
