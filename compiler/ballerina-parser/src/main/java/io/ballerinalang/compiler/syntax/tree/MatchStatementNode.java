/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerinalang.compiler.syntax.tree;

import io.ballerinalang.compiler.internal.parser.tree.STNode;

import java.util.Objects;

/**
 * This is a generated syntax tree node.
 *
 * @since 2.0.0
 */
public class MatchStatementNode extends StatementNode {

    public MatchStatementNode(STNode internalNode, int position, NonTerminalNode parent) {
        super(internalNode, position, parent);
    }

    public Token matchKeyword() {
        return childInBucket(0);
    }

    public ExpressionNode condition() {
        return childInBucket(1);
    }

    public Token openBrace() {
        return childInBucket(2);
    }

    public SeparatedNodeList<MatchClauseNode> matchClauses() {
        return new SeparatedNodeList<>(childInBucket(3));
    }

    public Token closeBrace() {
        return childInBucket(4);
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public <T> T apply(NodeTransformer<T> visitor) {
        return visitor.transform(this);
    }

    @Override
    protected String[] childNames() {
        return new String[]{
                "matchKeyword",
                "condition",
                "openBrace",
                "matchClauses",
                "closeBrace"};
    }

    public MatchStatementNode modify(
            Token matchKeyword,
            ExpressionNode condition,
            Token openBrace,
            SeparatedNodeList<MatchClauseNode> matchClauses,
            Token closeBrace) {
        if (checkForReferenceEquality(
                matchKeyword,
                condition,
                openBrace,
                matchClauses.underlyingListNode(),
                closeBrace)) {
            return this;
        }

        return NodeFactory.createMatchStatementNode(
                matchKeyword,
                condition,
                openBrace,
                matchClauses,
                closeBrace);
    }

    public MatchStatementNodeModifier modify() {
        return new MatchStatementNodeModifier(this);
    }

    /**
     * This is a generated tree node modifier utility.
     *
     * @since 2.0.0
     */
    public static class MatchStatementNodeModifier {
        private final MatchStatementNode oldNode;
        private Token matchKeyword;
        private ExpressionNode condition;
        private Token openBrace;
        private SeparatedNodeList<MatchClauseNode> matchClauses;
        private Token closeBrace;

        public MatchStatementNodeModifier(MatchStatementNode oldNode) {
            this.oldNode = oldNode;
            this.matchKeyword = oldNode.matchKeyword();
            this.condition = oldNode.condition();
            this.openBrace = oldNode.openBrace();
            this.matchClauses = oldNode.matchClauses();
            this.closeBrace = oldNode.closeBrace();
        }

        public MatchStatementNodeModifier withMatchKeyword(
                Token matchKeyword) {
            Objects.requireNonNull(matchKeyword, "matchKeyword must not be null");
            this.matchKeyword = matchKeyword;
            return this;
        }

        public MatchStatementNodeModifier withCondition(
                ExpressionNode condition) {
            Objects.requireNonNull(condition, "condition must not be null");
            this.condition = condition;
            return this;
        }

        public MatchStatementNodeModifier withOpenBrace(
                Token openBrace) {
            Objects.requireNonNull(openBrace, "openBrace must not be null");
            this.openBrace = openBrace;
            return this;
        }

        public MatchStatementNodeModifier withMatchClauses(
                SeparatedNodeList<MatchClauseNode> matchClauses) {
            Objects.requireNonNull(matchClauses, "matchClauses must not be null");
            this.matchClauses = matchClauses;
            return this;
        }

        public MatchStatementNodeModifier withCloseBrace(
                Token closeBrace) {
            Objects.requireNonNull(closeBrace, "closeBrace must not be null");
            this.closeBrace = closeBrace;
            return this;
        }

        public MatchStatementNode apply() {
            return oldNode.modify(
                    matchKeyword,
                    condition,
                    openBrace,
                    matchClauses,
                    closeBrace);
        }
    }
}
