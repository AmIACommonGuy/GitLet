package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Ziming Huang
 */
public class Main {
    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */

    public static void main(String[] args) {
//        String[] args = new String[]{"status"};
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        }
        Repository gitLet = new Repository(args[0]);
        //command call
        switch (args[0]) {
            case "init":
                Repository.setupPersistence();
                gitLet.init();
                break;
            case "add":
                String fileName = args[1];
                gitLet.add(fileName);
                break;
            case "commit":
                if (args.length == 1 | args[1].equals("")) {
                    System.out.print("Please enter a commit message.");
                    break;
                }
                String commitInfo = args[1];
                gitLet.commit(commitInfo);
                break;
            case "checkout":
                if (args.length == 2) {
                    gitLet.checkoutBranch(args[1]);
                } else if ((args.length == 3) && (args[1].equals("--"))) {
                    gitLet.checkout(args[2]);
                } else if ((args.length == 4) && (args[2].equals("--"))) {
                    gitLet.checkout(args[1], args[3]);
                } else {
                    System.out.print("Incorrect operands.");
                    break;
                }
                break;
            case "log":
                gitLet.log();
                break;
            case "branch":
                gitLet.branch(args[1]);
                break;
            case "status":
                gitLet.status();
                break;
            case "rm":
                gitLet.rm(args[1]);
                break;
            case "global-log":
                gitLet.globalLog();
                break;
            case "find":
                gitLet.find(args[1]);
                break;
            case "rm-branch":
                gitLet.removeBranch(args[1]);
                break;
            case "reset":
                gitLet.reset(args[1]);
                break;
            case "merge":
                gitLet.merge(args[1]);
                break;
            default:
                System.out.print("No command with that name exists.");
                break;
        }
    }
}
