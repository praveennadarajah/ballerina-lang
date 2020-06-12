
function commitExpMissingInTransactionStmt(int i) returns (string) {
    string a = "start";

    transaction {
        a = a + " inTrx";
        if (i == 0) {
            a = a + " rollback";
            rollback;
        }
        rollback;
        a = a + " endTrx";
    }
    return a;
}

transactional function txStmtWithinTransactionalScope(int i) returns (string) {
    string a = "start";
    var o = testTransactionalInvo(a);
    transaction {
        a = a + " inTrx";
        if (i == -1) {
            a = a + " rollback";
            rollback;
        }
        var res = commit;
        a = a + " endTrx";
    }
    var c = start testInvo(a);
    return a;
}

function invocationsWithinTx(int i) returns (string) {
   string a = "start";

   transaction {
      a = a + " inTrx";
      var b = start testInvo(a);
      var c = commit;
      var d = testTransactionalInvo(a);
   }
   return a;
}

function txWithMultiplePaths(int i)  {
    string a = "start";

    transaction {
        a = a + " inTrx";
        if(i == 3) {
            a = a + " inIf3";
            var b = testTransactionalInvo(a);
            var o = commit;
        } else {
            a = a + " inElse";
            var b = testTransactionalInvo(a);
            rollback;
        }
        var o = commit;
    }

    transaction {
        a = a + " inTrx";
        if(i == 3) {
            a = a + " inIf3";
            var b = testTransactionalInvo(a);
            var o = commit;
        } else if (i == 5) {
            a = a + " inIf5";
            var b = testTransactionalInvo(a);
            //var o = commit;
        }
        var o = commit;
    }
}

function testInvo(string str) returns string {
 return str + " non-transactional call";
}

transactional function testTransactionalInvo(string str) returns string {
    return str + " transactional call";
}


function testTransactionRollback() {
    int i = 10;
    transaction {
        i = i + 1;
        if (i > 10) {
            rollback;
        }
        while (i < 40) {
            i = i + 2;
            if (i == 44) {
                rollback;
                int k = 9;
            }
        }
        rollback;
        i = i + 2;
        var o = commit;
    }
}

function testBreakWithinTransaction() returns (string) {
    int i = 0;
    while (i < 5) {
        i = i + 1;
        transaction {
            if (i == 2) {
                var o = commit;
                break;
            }
        }
        transaction {
            if (i == 4) {
                break;
            }
        }
    }
    var o = commit;
    return "done";
}

function testNextWithinTransaction() returns (string) {
    int i = 0;
    while (i < 5) {
        i = i + 1;
        transaction {
            if (i == 2) {
                continue;
            } else {
                var o = commit;
            }
        }
    }
    return "done";
}

function testReturnWithinTransaction() returns (string) {
    int i = 0;
    while (i < 5) {
        i = i + 1;
        transaction {
            if (i == 2) {
                return "ff";
            } else {
                var o = commit;
            }
        }
    }
    return "done";
}

function testInvalidDoneWithinTransaction() {
    string workerTest = "";

    int i = 0;
    transaction {
        workerTest = workerTest + " withinTx";
        if (i == 0) {
            workerTest = workerTest + " beforeDone";
            return;
        } else {
            var o = commit;
        }
    }
    workerTest = workerTest + " beforeReturn";
    return;
}

function testReturnWithinMatchWithinTransaction() returns (string) {
    int i = 0;
    string|int unionVar = "test";
    while (i < 5) {
        i = i + 1;
        transaction {
            if (unionVar is string) {
                if (i == 2) {
                    var o = commit;
                    return "ff";
                } else {
                    return "ff";
                }
            } else {
                if (i == 2) {
                    return "ff";
                } else {
                    var o = commit;
                    return "ff";
                }
            }
        }
    }
    return "done";
}

function isTransactionalBlockFunc(string str) returns string {
    if transactional {
        if (str == "test") {
            rollback;
        } else {
            var rslt = testTransactionalInvo(str);
        }
    }
    var rslt = testTransactionalInvo(str);
    return str + " non-transactional call";
}
