/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.ballerinalang.compiler.desugar;

import org.ballerinalang.model.TreeBuilder;
import org.ballerinalang.model.tree.OperatorKind;
import org.ballerinalang.model.types.TypeKind;
import org.wso2.ballerinalang.compiler.semantics.analyzer.SymbolResolver;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BOperatorSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.types.BInvokableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BUnionType;
import org.wso2.ballerinalang.compiler.tree.BLangBlockFunctionBody;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangCommitExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangGroupExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLambdaFunction;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangStatementExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTransactionalExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTrapExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeTestExpr;
import org.wso2.ballerinalang.compiler.tree.statements.BLangAssignment;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBlockStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangExpressionStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangIf;
import org.wso2.ballerinalang.compiler.tree.statements.BLangRetryTransaction;
import org.wso2.ballerinalang.compiler.tree.statements.BLangRollback;
import org.wso2.ballerinalang.compiler.tree.statements.BLangSimpleVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTransaction;
import org.wso2.ballerinalang.compiler.tree.statements.BLangWhile;
import org.wso2.ballerinalang.compiler.tree.types.BLangType;
import org.wso2.ballerinalang.compiler.tree.types.BLangValueType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;
import org.wso2.ballerinalang.util.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static org.wso2.ballerinalang.compiler.util.Names.CLEAN_UP_TRANSACTION;
import static org.wso2.ballerinalang.compiler.util.Names.CURRENT_TRANSACTION_INFO;
import static org.wso2.ballerinalang.compiler.util.Names.END_TRANSACTION;
import static org.wso2.ballerinalang.compiler.util.Names.GET_AND_CLEAR_FAILURE_TRANSACTION;
import static org.wso2.ballerinalang.compiler.util.Names.ROLLBACK_TRANSACTION;
import static org.wso2.ballerinalang.compiler.util.Names.START_TRANSACTION;
import static org.wso2.ballerinalang.compiler.util.Names.TRANSACTION_INFO_RECORD;

/**
 * Class responsible for desugar transaction statements into actual Ballerina code.
 *
 * @since 2.0.0-preview1
 */
public class TransactionDesugar extends BLangNodeVisitor {

    private static final CompilerContext.Key<TransactionDesugar> TRANSACTION_DESUGAR_KEY = new CompilerContext.Key<>();
    private final Desugar desugar;
    private final SymbolTable symTable;
    private final SymbolResolver symResolver;
    private final Names names;

    private Stack<BSymbol> transactionErrorStack;
    private Stack<BLangExpression> retryStmtStack;

    private BLangExpression transactionBlockID;
    private BLangExpression transactionID;
    private BLangSimpleVarRef prevAttemptInfoRef;

    private TransactionDesugar(CompilerContext context) {
        context.put(TRANSACTION_DESUGAR_KEY, this);
        this.symTable = SymbolTable.getInstance(context);
        this.symResolver = SymbolResolver.getInstance(context);
        this.names = Names.getInstance(context);
        this.desugar = Desugar.getInstance(context);
        this.transactionErrorStack = new Stack<>();
        this.retryStmtStack = new Stack<>();
    }

    public static TransactionDesugar getInstance(CompilerContext context) {
        TransactionDesugar desugar = context.get(TRANSACTION_DESUGAR_KEY);
        if (desugar == null) {
            desugar = new TransactionDesugar(context);
        }
        return desugar;
    }

    BLangStatementExpression desugar(BLangTransaction transactionNode, SymbolEnv env) {
        // Transaction statement desugar implementation code.

        DiagnosticPos pos = transactionNode.pos;
        BLangBlockStmt transactionBlockStmt = desugarTransactionBody(transactionNode, env, false, pos);
        return ASTBuilderUtil.createStatementExpression(transactionBlockStmt,
                ASTBuilderUtil.createLiteral(pos, symTable.nilType, Names.NIL_VALUE));
    }

    private BLangBlockStmt desugarTransactionBody(BLangTransaction transactionNode, SymbolEnv env, boolean shouldRetry,
                                                  DiagnosticPos pos) {
        BLangBlockStmt transactionBlockStmt = ASTBuilderUtil.createBlockStmt(pos);

        // Create transaction block ID variable
        BLangLiteral transactionBlockIDLiteral = ASTBuilderUtil.createLiteral(pos, symTable.stringType,
                desugar.getTransactionBlockId());
        BType transactionBlockIDType = symTable.stringType;
        BVarSymbol transactionBlockIDVarSymbol = new BVarSymbol(0, new Name("transactionBlockId"),
                env.scope.owner.pkgID, transactionBlockIDType, env.scope.owner);
        BLangSimpleVariable transactionBlockIDVariable = ASTBuilderUtil.createVariable(pos, "transactionBlockId",
                transactionBlockIDType, transactionBlockIDLiteral, transactionBlockIDVarSymbol);
        transactionBlockIDVariable.symbol.closure = true;
        BLangSimpleVariableDef transactionBlockIDVariableDef = ASTBuilderUtil.createVariableDef(pos,
                transactionBlockIDVariable);
        transactionBlockStmt.stmts.add(transactionBlockIDVariableDef);

        BLangSimpleVariableDef prevAttemptVarDef = createPrevAttemptInfoVarDef(env, pos);
        transactionBlockStmt.stmts.add(prevAttemptVarDef);
        this.prevAttemptInfoRef = ASTBuilderUtil.createVariableRef(pos, prevAttemptVarDef.var.symbol);
        this.transactionBlockID = ASTBuilderUtil.createVariableRef(pos, transactionBlockIDVariable.symbol);

        // Invoke startTransaction method and get a transaction id
        BType transactionIDType = symTable.stringType;
        BVarSymbol transactionIDVarSymbol = new BVarSymbol(0, new Name("transactionId"),
                env.scope.owner.pkgID, transactionIDType, env.scope.owner);
        BLangSimpleVariable transactionIDVariable = ASTBuilderUtil.createVariable(pos, "transactionId",
                transactionIDType, ASTBuilderUtil.createLiteral(pos, symTable.stringType, ""), transactionIDVarSymbol);
        BLangSimpleVariableDef transactionIDVariableDef = ASTBuilderUtil.createVariableDef(pos,
                transactionIDVariable);
        transactionBlockStmt.stmts.add(transactionIDVariableDef);

        this.transactionID = ASTBuilderUtil.createVariableRef(pos, transactionIDVariable.symbol);
        transactionIDVariable.symbol.closure = true;

        env.scope.define(transactionBlockIDVarSymbol.name, transactionBlockIDVarSymbol);
        env.scope.define(transactionIDVarSymbol.name, transactionIDVarSymbol);
        env.scope.define(prevAttemptVarDef.var.symbol.name, prevAttemptVarDef.var.symbol);

        BLangBlockFunctionBody trxMainBody = ASTBuilderUtil.createBlockFunctionBody(transactionNode.pos);
        BLangType transactionLambdaReturnType = ASTBuilderUtil.createTypeNode(symTable.errorOrNilType);
        BLangSimpleVariable trxMainFuncParamPrevAttempt = createPrevAttemptVariable(env, pos);
        BLangLambdaFunction trxMainFunc = desugar.createLambdaFunction(transactionNode.pos, "$trxFunc$",
                Lists.of(trxMainFuncParamPrevAttempt), transactionLambdaReturnType, trxMainBody);

        BLangInvocation startTransactionInvocation =
                createStartTransactionInvocation(env, pos, transactionBlockIDLiteral,
                ASTBuilderUtil.createVariableRef(pos, trxMainFuncParamPrevAttempt.symbol));
        BLangAssignment startTrxAssignment =
                ASTBuilderUtil.createAssignmentStmt(pos, ASTBuilderUtil.createVariableRef(pos, transactionIDVarSymbol),
                startTransactionInvocation);

        BLangAssignment infoAssignment = createPrevAttemptInfoInvocation(env, pos);
        trxMainBody.stmts.add(startTrxAssignment);
        trxMainBody.stmts.add(infoAssignment);
        trxMainBody.stmts.addAll(transactionNode.transactionBody.stmts);

        BVarSymbol transactionVarSymbol = new BVarSymbol(0, names.fromString("$trxFunc$"),
                env.scope.owner.pkgID, trxMainFunc.type, trxMainFunc.function.symbol);
        BLangSimpleVariable transactionLambdaVariable = ASTBuilderUtil.createVariable(pos, "trxFunc",
                trxMainFunc.type, trxMainFunc, transactionVarSymbol);
        BLangSimpleVariableDef transactionLambdaVariableDef = ASTBuilderUtil.createVariableDef(pos,
                transactionLambdaVariable);
        BLangSimpleVarRef transactionLambdaVarRef = new BLangSimpleVarRef.
                BLangLocalVarRef(transactionLambdaVariable.symbol);
        transactionBlockStmt.stmts.add(transactionLambdaVariableDef);

        // Add lambda function call
        BLangInvocation transactionLambdaInvocation = new BLangInvocation.BFunctionPointerInvocation(pos,
                transactionLambdaVarRef, transactionLambdaVariable.symbol, symTable.errorOrNilType);
        transactionLambdaInvocation.argExprs = Lists.of(desugar.rewrite(prevAttemptInfoRef, env));
        transactionLambdaInvocation.requiredArgs = transactionLambdaInvocation.argExprs;
        BLangTrapExpr trapExpr = (BLangTrapExpr) TreeBuilder.createTrapExpressionNode();
        trapExpr.type = BUnionType.create(null, symTable.errorType, symTable.nilType);
        trapExpr.expr = transactionLambdaInvocation;

        trxMainFunc.capturedClosureEnv = env;

        BVarSymbol resultSymbol = new BVarSymbol(0, new Name("result"),
                env.scope.owner.pkgID, symTable.errorOrNilType, env.scope.owner);
        BLangSimpleVariable resultVariable = ASTBuilderUtil.createVariable(pos, "result",
                symTable.errorOrNilType, trapExpr, resultSymbol);
        BLangSimpleVariableDef trxFuncVarDef = ASTBuilderUtil.createVariableDef(pos,
                resultVariable);

        if (shouldRetry) {
            transactionErrorStack.push(resultSymbol);
            retryStmtStack.push(trapExpr);
        }

        transactionBlockStmt.stmts.add(trxFuncVarDef);

        createRollbackIfFailed(transactionNode.pos, transactionBlockStmt, resultSymbol);
        return transactionBlockStmt;
    }

    private BLangAssignment createPrevAttemptInfoInvocation(SymbolEnv env, DiagnosticPos pos) {
        BInvokableSymbol transactionInfoInvokableSymbol = (BInvokableSymbol) symResolver.
                lookupSymbolInMainSpace(symTable.pkgEnvMap.get(desugar.getTransactionSymbol(env)),
                        CURRENT_TRANSACTION_INFO);
        BLangInvocation infoInvocation =
                ASTBuilderUtil.createInvocationExprForMethod(pos, transactionInfoInvokableSymbol,
                        new ArrayList<>(), symResolver);
        infoInvocation.argExprs = infoInvocation.requiredArgs;
        return ASTBuilderUtil.createAssignmentStmt(pos, prevAttemptInfoRef, infoInvocation);
    }

    private BLangInvocation createStartTransactionInvocation(SymbolEnv env, DiagnosticPos pos,
            BLangLiteral transactionBlockIDLiteral, BLangSimpleVarRef prevAttempt) {
        BInvokableSymbol startTransactionInvokableSymbol = (BInvokableSymbol) symResolver.
                lookupSymbolInMainSpace(symTable.pkgEnvMap.get(desugar.getTransactionSymbol(env)), START_TRANSACTION);
        List<BLangExpression> args = new ArrayList<>();
        args.add(transactionBlockIDLiteral);
        args.add(prevAttempt);
        BLangInvocation startTransactionInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(pos, startTransactionInvokableSymbol, args, symResolver);
        startTransactionInvocation.argExprs = args;
        return startTransactionInvocation;
    }

    private BLangSimpleVariableDef createPrevAttemptInfoVarDef(SymbolEnv env, DiagnosticPos pos) {
        BLangLiteral nilLiteral = ASTBuilderUtil.createLiteral(pos, symTable.nilType, Names.NIL_VALUE);
        BLangSimpleVariable prevAttemptVariable = createPrevAttemptVariable(env, pos);
        prevAttemptVariable.expr = nilLiteral;
        return ASTBuilderUtil.createVariableDef(pos, prevAttemptVariable);
    }

    private BLangSimpleVariable createPrevAttemptVariable(SymbolEnv env, DiagnosticPos pos) {
        BSymbol infoRecordSymbol = symResolver.
                lookupSymbolInMainSpace(symTable.pkgEnvMap.get(desugar.getTransactionSymbol(env)),
                TRANSACTION_INFO_RECORD);
        BType infoRecordType = BUnionType.create(null, infoRecordSymbol.type, symTable.nilType);
        BVarSymbol prevAttemptVarSymbol = new BVarSymbol(0, new Name("prevAttempt"),
                env.scope.owner.pkgID, infoRecordType, env.scope.owner);
        prevAttemptVarSymbol.closure = true;
        return ASTBuilderUtil.createVariable(pos, "prevAttempt", infoRecordType, null, prevAttemptVarSymbol);
    }

    //  commit or rollback was not executed and fail(e) or panic(e) returned, so rollback
    //    if (transactional && result is error) {
    //        rollback result;
    //    }
    private void createRollbackIfFailed(DiagnosticPos pos, BLangBlockStmt transactionBlockStmt,
                                        BSymbol trxFuncResultSymbol) {
        BLangIf rollbackCheck = ASTBuilderUtil.createIfStmt(pos, transactionBlockStmt);
        BLangTransactionalExpr transactionalExpr = TreeBuilder.createTransactionalExpressionNode();
        transactionalExpr.type = symTable.booleanType;
        BLangSimpleVarRef result = ASTBuilderUtil.createVariableRef(pos, trxFuncResultSymbol);
        BLangTypeTestExpr errorCheck = desugar.createTypeCheckExpr(pos, result, desugar.getErrorTypeNode());
        rollbackCheck.expr = ASTBuilderUtil.createBinaryExpr(pos, transactionalExpr, errorCheck, symTable.booleanType,
                OperatorKind.AND, null);
        BLangRollback rollbackStmt = (BLangRollback) TreeBuilder.createRollbackNode();
        rollbackStmt.expr = result;
        rollbackCheck.body = ASTBuilderUtil.createBlockStmt(pos);
        rollbackCheck.body.stmts.add(rollbackStmt);
    }

    private BLangInvocation createCleanupTrxStmt(DiagnosticPos pos,  SymbolEnv env) {
        List<BLangExpression> args;
        BInvokableSymbol cleanupTrxInvokableSymbol = (BInvokableSymbol) symResolver.lookupSymbolInMainSpace(symTable
                        .pkgEnvMap.get(desugar.getTransactionSymbol(env)), CLEAN_UP_TRANSACTION);
        args = new ArrayList<>();
        args.add(transactionBlockID);
        BLangInvocation cleanupTrxInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(pos, cleanupTrxInvokableSymbol, args, symResolver);
        cleanupTrxInvocation.argExprs = args;
        return cleanupTrxInvocation;
    }

    BLangStatementExpression desugar(BLangRollback rollbackNode, SymbolEnv env) {
        // Rollback desugar implementation
        DiagnosticPos pos = rollbackNode.pos;
        BLangBlockStmt rollbackBlockStmt = ASTBuilderUtil.createBlockStmt(pos);
        BSymbol transactionSymbol = desugar.getTransactionSymbol(env);

        // Create temp output variable
        BLangSimpleVariableDef outputVariableDef = createRollbackOrCommitResultDef(env, pos);
        BLangSimpleVarRef outputVarRef = ASTBuilderUtil.createVariableRef(pos, outputVariableDef.var.symbol);
        rollbackBlockStmt.addStatement(outputVariableDef);

        BInvokableSymbol rollbackTransactionInvokableSymbol = (BInvokableSymbol) symResolver
                .lookupSymbolInMainSpace(symTable.pkgEnvMap.get(transactionSymbol), ROLLBACK_TRANSACTION);
        List<BLangExpression> args = new ArrayList<>();
        args.add(transactionBlockID);
        if (rollbackNode.expr != null) {
            args.add(rollbackNode.expr);
        }
        BLangInvocation rollbackTransactionInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(pos, rollbackTransactionInvokableSymbol, args, symResolver);
        rollbackTransactionInvocation.argExprs = args;

        BLangTrapExpr rollbackTrapExpression = (BLangTrapExpr) TreeBuilder.createTrapExpressionNode();
        rollbackTrapExpression.type = BUnionType.create(null, symTable.errorType, symTable.nilType);
        rollbackTrapExpression.expr = rollbackTransactionInvocation;

        BVarSymbol rollbackTransactionVarSymbol = new BVarSymbol(0, new Name("rollbackResult"),
                env.scope.owner.pkgID, symTable.errorOrNilType, env.scope.owner);
        BLangSimpleVariable rollbackResultVariable = ASTBuilderUtil.createVariable(pos, "rollbackResult",
                symTable.errorOrNilType, rollbackTrapExpression, rollbackTransactionVarSymbol);
        rollbackBlockStmt.stmts.add(ASTBuilderUtil.createVariableDef(pos,
                rollbackResultVariable));
        BLangSimpleVarRef rollbackResult = ASTBuilderUtil.createVariableRef(pos, rollbackTransactionVarSymbol);
        BLangIf checkResult = ASTBuilderUtil.createIfStmt(pos, rollbackBlockStmt);
        checkResult.expr = desugar.createTypeCheckExpr(pos, rollbackResult, desugar.getErrorTypeNode());
        checkResult.body = ASTBuilderUtil.createBlockStmt(pos);
        checkResult.body.stmts.add(ASTBuilderUtil.createAssignmentStmt(pos, outputVarRef, rollbackResult));
        BLangBlockStmt elseStmt = ASTBuilderUtil.createBlockStmt(pos);
        BLangExpressionStmt cleanupTrxStmt = ASTBuilderUtil.createExpressionStmt(pos, elseStmt);
        cleanupTrxStmt.expr = createCleanupTrxStmt(pos, env);
        checkResult.elseStmt = elseStmt;
        BLangStatementExpression rollbackStmtExpr =
                ASTBuilderUtil.createStatementExpression(rollbackBlockStmt, outputVarRef);
        rollbackStmtExpr.type = symTable.errorOrNilType;
        return rollbackStmtExpr;
    }

    BLangStatementExpression desugar(BLangCommitExpr commitExpr, SymbolEnv env) {
        DiagnosticPos pos = commitExpr.pos;
        BLangBlockStmt commitBlockStatement = ASTBuilderUtil.createBlockStmt(pos);
        BSymbol transactionSymbol = desugar.getTransactionSymbol(env);

        // Create temp output variable
        BLangSimpleVariableDef outputVariableDef = createRollbackOrCommitResultDef(env, pos);
        BLangSimpleVarRef outputVarRef = ASTBuilderUtil.createVariableRef(pos, outputVariableDef.var.symbol);
        commitBlockStatement.addStatement(outputVariableDef);

        // Clear failures
        BInvokableSymbol transactionCleanerInvokableSymbol = (BInvokableSymbol) symResolver.
                lookupSymbolInMainSpace(symTable.pkgEnvMap.get(transactionSymbol),
                        GET_AND_CLEAR_FAILURE_TRANSACTION);
        BLangInvocation transactionCleanerInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(pos, transactionCleanerInvokableSymbol, new ArrayList<>(), symResolver);
        transactionCleanerInvocation.argExprs = new ArrayList<>();

        BVarSymbol isTransactionFailedVarSymbol = new BVarSymbol(0, new Name("isFailed"),
                env.scope.owner.pkgID, symTable.booleanType, env.scope.owner);
        BLangSimpleVariable isTransactionFailedVariable = ASTBuilderUtil.createVariable(pos, "isFailed",
                symTable.booleanType, transactionCleanerInvocation, isTransactionFailedVarSymbol);
        BLangSimpleVariableDef isTransactionFailedVariableDef = ASTBuilderUtil.createVariableDef(pos,
                isTransactionFailedVariable);
        commitBlockStatement.addStatement(isTransactionFailedVariableDef);

        BLangBlockStmt failureHandlerBlockStatement = ASTBuilderUtil.createBlockStmt(pos);

        // Commit expr desugar implementation
        BInvokableSymbol commitTransactionInvokableSymbol = (BInvokableSymbol) symResolver.
                lookupSymbolInMainSpace(symTable.pkgEnvMap.get(transactionSymbol),
                        END_TRANSACTION);
        List<BLangExpression> args = new ArrayList<>();
        args.add(transactionID);
        args.add(transactionBlockID);
        BLangInvocation commitTransactionInvocation = ASTBuilderUtil.
                createInvocationExprForMethod(pos, commitTransactionInvokableSymbol, args, symResolver);
        commitTransactionInvocation.argExprs = args;

        BLangTrapExpr commitTrapExpression = (BLangTrapExpr) TreeBuilder.createTrapExpressionNode();
        BType commitReturnType = BUnionType.create(null, symTable.stringType, symTable.errorType);
        commitTrapExpression.type = commitReturnType;
        commitTrapExpression.expr = commitTransactionInvocation;

        BVarSymbol commitTransactionVarSymbol = new BVarSymbol(0, new Name("commitResult"),
                env.scope.owner.pkgID, commitReturnType, env.scope.owner);
        BLangSimpleVariable commitResultVariable = ASTBuilderUtil.createVariable(pos, "commitResult",
                commitReturnType, commitTrapExpression, commitTransactionVarSymbol);
        BLangSimpleVariableDef commitResultVariableDef = ASTBuilderUtil.createVariableDef(pos,
                commitResultVariable);
        BLangSimpleVarRef commitResultVarRef = ASTBuilderUtil.createVariableRef(pos, commitResultVariable.symbol);
        failureHandlerBlockStatement.addStatement(commitResultVariableDef);

        // Successful commit operation
        BLangIf commitResultValidationIf = ASTBuilderUtil.createIfStmt(pos, failureHandlerBlockStatement);
        BLangGroupExpr commitResultValidationGroupExpr = new BLangGroupExpr();
        commitResultValidationGroupExpr.type = symTable.booleanType;
        BLangValueType stringType = (BLangValueType) TreeBuilder.createValueTypeNode();
        stringType.type = symTable.stringType;
        stringType.typeKind = TypeKind.STRING;
        commitResultValidationGroupExpr.expression = ASTBuilderUtil.createTypeTestExpr(pos, commitResultVarRef,
                stringType);
        commitResultValidationIf.expr = commitResultValidationGroupExpr;
        commitResultValidationIf.body = ASTBuilderUtil.createBlockStmt(pos);
        BLangExpressionStmt stmt = ASTBuilderUtil.createExpressionStmt(pos, commitResultValidationIf.body);
        stmt.expr = createCleanupTrxStmt(pos, env);
        commitResultValidationIf.elseStmt = ASTBuilderUtil.createAssignmentStmt(pos, outputVarRef, commitResultVarRef);
        // Create failure validation
        BLangIf failureValidationIf = ASTBuilderUtil.createIfStmt(pos, commitBlockStatement);
        BLangGroupExpr failureValidationGroupExpr = new BLangGroupExpr();
        failureValidationGroupExpr.type = symTable.booleanType;
        BLangSimpleVarRef failureValidationExprVarRef = ASTBuilderUtil.createVariableRef(pos,
                isTransactionFailedVariable.symbol);
        List<BType> paramTypes = new ArrayList<>();
        paramTypes.add(symTable.booleanType);
        BInvokableType type = new BInvokableType(paramTypes, symTable.booleanType,
                null);
        BOperatorSymbol notOperatorSymbol = new BOperatorSymbol(
                names.fromString(OperatorKind.NOT.value()), symTable.rootPkgSymbol.pkgID, type, symTable.rootPkgSymbol);
        failureValidationGroupExpr.expression = ASTBuilderUtil.createUnaryExpr(pos, failureValidationExprVarRef,
                symTable.booleanType, OperatorKind.NOT, notOperatorSymbol);
        failureValidationIf.expr = failureValidationGroupExpr;
        failureValidationIf.body = failureHandlerBlockStatement;

        BLangStatementExpression stmtExpr = ASTBuilderUtil.createStatementExpression(commitBlockStatement,
                outputVarRef);
        stmtExpr.type = symTable.errorOrNilType;
        return stmtExpr;
    }

    private BLangSimpleVariableDef createRollbackOrCommitResultDef(SymbolEnv env, DiagnosticPos pos) {
        BLangExpression nilExpression = ASTBuilderUtil.createLiteral(pos, symTable.nilType, Names.NIL_VALUE);
        BVarSymbol outputVarSymbol = new BVarSymbol(0, new Name("$outputVar$"),
                env.scope.owner.pkgID, symTable.errorOrNilType, env.scope.owner);
        BLangSimpleVariable outputVariable =
                ASTBuilderUtil.createVariable(pos, "$outputVar$", symTable.errorOrNilType,
                        nilExpression, outputVarSymbol);
        return ASTBuilderUtil.createVariableDef(pos, outputVariable);
    }

    public BLangStatementExpression desugar(BLangRetryTransaction retryTrxBlock, SymbolEnv env) {
        BLangBlockStmt blockStmt = desugarTransactionBody(retryTrxBlock.transaction, env, true, retryTrxBlock.pos);
        BLangSimpleVariableDef retryMgrDef =
                desugar.createRetryManagerDef(retryTrxBlock.retrySpec, retryTrxBlock.pos);
        blockStmt.stmts.add(retryMgrDef);
        BLangSimpleVarRef resultRef = ASTBuilderUtil.createVariableRef(retryTrxBlock.pos, transactionErrorStack.pop());
        BLangWhile retryWhileLoop = desugar.createRetryWhileLoop(retryTrxBlock.pos, retryMgrDef, retryStmtStack.pop(),
                resultRef);
        createRollbackIfFailed(retryTrxBlock.pos, retryWhileLoop.body, resultRef.symbol);
        blockStmt.stmts.add(retryWhileLoop);
        return ASTBuilderUtil.createStatementExpression(blockStmt,
                ASTBuilderUtil.createLiteral(retryTrxBlock.pos, symTable.nilType, Names.NIL_VALUE));
    }
}
