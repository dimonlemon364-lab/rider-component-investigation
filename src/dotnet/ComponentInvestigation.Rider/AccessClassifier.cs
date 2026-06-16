using JetBrains.ReSharper.Psi;
using JetBrains.ReSharper.Psi.CSharp.Tree;
using JetBrains.ReSharper.Psi.Tree;
using JetBrains.Rider.Model;
using IReference = JetBrains.ReSharper.Psi.Resolve.IReference;

namespace ComponentInvestigation.Rider
{
    /// <summary>
    /// Maps a reference + its target element to a <see cref="UsageAccessKind"/>.
    /// Read/write classification is precise for C#; other languages fall back to Read.
    /// </summary>
    public static class AccessClassifier
    {
        public static UsageAccessKind Classify(IReference reference, IDeclaredElement target)
        {
            var node = reference.GetTreeNode();

            switch (target)
            {
                case IConstructor:
                    return UsageAccessKind.Instantiation;

                case ITypeElement:
                    return node.GetContainingNode<IObjectCreationExpression>() != null
                        ? UsageAccessKind.Instantiation
                        : UsageAccessKind.TypeUsage;

                case IFunction:
                    return UsageAccessKind.Invocation;

                case IField:
                case IProperty:
                case IEvent:
                    return ClassifyExpressionAccess(node);

                default:
                    return UsageAccessKind.Other;
            }
        }

        private static UsageAccessKind ClassifyExpressionAccess(ITreeNode node)
        {
            var refExpr = node as IReferenceExpression ?? node.GetContainingNode<IReferenceExpression>();
            if (refExpr == null)
                return UsageAccessKind.Read; // best-effort for non-C# languages

            // ++ / -- -> read + write.
            if (refExpr.Parent is IPostfixOperatorExpression || refExpr.Parent is IPrefixOperatorExpression)
                return UsageAccessKind.ReadWrite;

            // Assignment target.
            if (refExpr.Parent is IAssignmentExpression assignment && assignment.Dest == refExpr)
            {
                return assignment.AssignmentType == AssignmentType.EQ
                    ? UsageAccessKind.Write
                    : UsageAccessKind.ReadWrite; // compound assignment (+=, -=, ...)
            }

            return UsageAccessKind.Read;
        }
    }
}
