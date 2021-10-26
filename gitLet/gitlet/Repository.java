package gitlet;

import java.io.File;
import java.util.*;

import static gitlet.Utils.*;
import static gitlet.Utils.writeContents;

public class Repository {

    /** Only contains files that are eligible to be added.
     *  first is Filename, the second is SHA1 */
    private HashMap<String, String> addStage;
    /** sha1 */
    private HashSet<String> removeStage;
    /** Commit Tree. */
    private HashMap<String, Commit> commitHistory; //
    /** Current Commit SHA1 code. */
    private String headPos;
    /** Current Commit. */
    private Commit currentCommit; // We save this
    private String currentBranchName;

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The structure of the .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File BLOB_DIR = join(GITLET_DIR, "BLOB");
    public static final File STAGE_DIR = join(GITLET_DIR, "STAGE");
    public static final File BRANCH_DIR = join(GITLET_DIR, "BRANCH");
    public static final File WORK_DIR = join(GITLET_DIR, "ACTIVE");
    public static final File REMOVE_DIR = join(GITLET_DIR, "REMOVE");
    public static final File SPLIT_DIR = join(BRANCH_DIR, "SPLIT");
    /** Current Commit Head */
    private static final File CURRENT_COMMIT_SAVE = join(WORK_DIR, "currentCommitSave");
    /** Contains the current branch name */
    private static final File CURRENT_BRANCH = join(WORK_DIR, "activeBranch");

    /** Create the working directory. */
    public static void setupPersistence() {
        // Check exist
        if (BLOB_DIR.exists()) {
            System.out.println("A Gitlet version-control system "
                    + "already exists in the current directory.");
            System.exit(0);
        }
        BLOB_DIR.mkdirs();
        STAGE_DIR.mkdirs();
        BRANCH_DIR.mkdirs();
        WORK_DIR.mkdirs();
        REMOVE_DIR.mkdirs();
        SPLIT_DIR.mkdirs();
    }
    // Load existed git log. If not init(), return.
    public Repository(String command) {
        // never init(), load save is not needed.
        if (!BLOB_DIR.exists() && command.equals("init")) {
            return;
        } else if (!BLOB_DIR.exists() && !command.equals("init")) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
        // get the head Commit. Should be the menu of the current git folder
        this.currentCommit = readObject(CURRENT_COMMIT_SAVE, Commit.class);

        // Important Collection classes
        addStage = new HashMap<>();
        removeStage = new HashSet<String>();
        this.stageBuilder(); // build current stage based on added file
        this.removeStageBuilder(); // build removeStage based on the removal list.

        // Important variables
        this.headPos = this.currentCommit.getID();
        this.currentBranchName = readContentsAsString(CURRENT_BRANCH);
    }

    /** init(): Create sentinel Commit. */
    public void init() {
        Commit sentinel = new Commit("initial commit", null);
        /** Track the base files */
        headPos = sentinel.getID();
        currentCommit = sentinel;
        // The first sha1 commit save
        File commitSave = join(GITLET_DIR, headPos);
        writeObject(commitSave, currentCommit);
        // CurrentCommitSave: with only sentinel
        writeObject(CURRENT_COMMIT_SAVE, currentCommit);
        // New brach master and Set the current branch to master
        currentBranchName = "master";
        branch("master");
        writeContents(CURRENT_BRANCH, currentBranchName);
    }

    /** Add the marked file */
    public void add(String fileName) {
        // Untracked file
        File stagingFile = join(CWD, fileName);
        // The save in Stage
        File stagedFile = join(STAGE_DIR, fileName);

        /** Check existency */
        if (!stagingFile.exists()) {
            System.out.println("File does not exist.");
            return;
        }
        /** Check remove */
        /** Update rmFiles to restore rmf */
        File rmf = join(REMOVE_DIR, fileName);
        rmf.delete();
        /** File exists!
         *  If current commit has nothing, add it!
         *  If current commit have it check version! */
        if (currentCommit.getList() != null
                && currentCommit.getList().containsKey(fileName)) {
            File oldVersion = join(BLOB_DIR, currentCommit.getList().get(fileName));
            /** Check content */
            if (compareFile(stagingFile, oldVersion)) {
                /** same as remove */
                addStage.remove(fileName);
                stagedFile.delete();
                return;
            }
        }
        // All saving File related uses read/write contents
        byte[] content = readContents(stagingFile);
        String version = sha1(content);
        addStage.put(fileName, version);
        // Note: it supports overwrite.
        writeContents(stagedFile, content);
    }


    /** It unstages files & remove File */
    public void rm(String fileName) {

        HashMap<String, String> currentList = currentCommit.getList();
        if (currentList.containsKey(fileName)) {
            File rmF = join(REMOVE_DIR, fileName);
            removeStage.add(fileName);
            // Delete the current file in the working folder.
            restrictedDelete(join(CWD, fileName));
            // Save the current remove stage and put version number of that file in the save
            String versionNum = currentList.get(fileName);
            writeContents(rmF, versionNum);
        } else {
            /** Check Stage for added file */
            File stagedFile = join(STAGE_DIR, fileName);
            if (!stagedFile.exists()) {
                System.out.println("No reason to remove the file.");
            } else {
                /** no need to update Stage tho */
                stagedFile.delete();
                addStage.remove(fileName);
            }
        }
        return;
    }

    /** Create new Commit and update Blob */
    public void commit(String commitInfo) {
        /** Check for empty stage */
        if (addStage.isEmpty() && removeStage.isEmpty()) {
            System.out.print("No changes added to the commit.");
            return;
        }
        /** Update Commit history */
        Commit newCommit = new Commit(commitInfo, headPos);
        newCommit.addStaged(currentCommit.getList());
        /** removeStage contains all filename for removal
         *  files for removal can't be in Stage (unstaged already)*/
        for (String i : removeStage) {
            newCommit.getList().remove(i);
        }
        /** put all newly added file record to newCommit.getList() */
        newCommit.addStaged(addStage);
        /** Saving process */
        currentCommit = newCommit;
        headPos = currentCommit.getID();
        writeObject(CURRENT_COMMIT_SAVE, currentCommit);
        File newCommitSave = join(GITLET_DIR, currentCommit.getID());
        writeObject(newCommitSave, currentCommit);
        writeContents(findBranch(currentBranchName), newCommit.getID());

        /** Update Blob */
        for (String i : addStage.keySet()) {
            File stagedFile = join(STAGE_DIR, i);
            byte[] content = readContents(stagedFile);
            /** name the new Blob by the file SHA1 code (version number) */
            File newBlob = join(BLOB_DIR, newCommit.getList().get(i));
            writeContents(newBlob, content);
        }

        /** Clear Stage, only needs to remove the temp Files in stage */
        for (String i : plainFilenamesIn(STAGE_DIR)) {
            File delFile = join(STAGE_DIR, i);
            delFile.delete();
        }
        /** Clear removeStage */
        for (String i : plainFilenamesIn(REMOVE_DIR)) {
            File delFile = join(REMOVE_DIR, i);
            delFile.delete();
        }
    }

    private void mergeCommit(String mainName, String mergeInName, String mainID, String mergeInID) {
        /** Check for empty stage */
        if (addStage.isEmpty() && removeStage.isEmpty()) {
            return;
        }
        /** Update Commit history */
        Commit newCommit = new Commit(mainName, mergeInName, mainID, mergeInID);
        newCommit.addStaged(currentCommit.getList());
        /** removeStage contains all filename for removal
         *  files for removal can't be in Stage (unstaged already)*/
        for (String i : removeStage) {
            newCommit.getList().remove(i);
        }
        /** put all newly added file record to newCommit.getList() */
        newCommit.addStaged(addStage);
        /** Saving process */
        currentCommit = newCommit;
        writeObject(CURRENT_COMMIT_SAVE, currentCommit);
        File newCommitSave = join(GITLET_DIR, currentCommit.getID());
        writeObject(newCommitSave, currentCommit);
        writeContents(findBranch(currentBranchName), newCommit.getID());

        /** Update Blob */
        for (String i : addStage.keySet()) {
            File stagedFile = join(STAGE_DIR, i);
            byte[] content = readContents(stagedFile);
            /** name the new Blob by the file SHA1 code (version number) */
            File newBlob = join(BLOB_DIR, newCommit.getList().get(i));
            writeContents(newBlob, content);
        }
        /** Clear Stage, only needs to remove the temp Files in stage */
        for (String i : plainFilenamesIn(STAGE_DIR)) {
            File delFile = join(STAGE_DIR, i);
            delFile.delete();
        }
        /** Clear removeStage */
        for (String i : plainFilenamesIn(REMOVE_DIR)) {
            File delFile = join(REMOVE_DIR, i);
            delFile.delete();
        }
    }

    private void logHelper(Commit c) {
        if (c.getParent() == null) {
            System.out.println("===");
            System.out.println("commit " + c.getID());
            if (c.isMerge()) {
                System.out.println(c.getParents());
            }
            System.out.println("Date: " + c.getTimestamp());
            System.out.println(c.getMessage());
            return;
        }
        System.out.println("===");
        System.out.println("commit " + c.getID());
        if (c.isMerge()) {
            System.out.println(c.getParents());
        }
        System.out.println("Date: " + c.getTimestamp());
        System.out.println(c.getMessage());
        System.out.println();
        logHelper(commitHistory.get(c.getParent()));
    }

    /** Only specify commitHistory over here */
    public void log() {
        /** Traverse back from current Commit Head */
        this.commitHistory = new HashMap<>();
        branchBuilder();
        logHelper(currentCommit);
    }

    /** Checkout case 1: fix the version of file */
    public void checkout(String sha1Code, String filename) {
        if (sha1Code.length() != 40) {
            sha1Code = findID(sha1Code);
        }
        File targetCommitFile = join(GITLET_DIR, sha1Code);
        if (!targetCommitFile.exists()) {
            System.out.print("No commit with that id exists.");
            System.exit(0);
        }
        HashMap<String, String> targetList = readObject(targetCommitFile, Commit.class).getList();
        if (targetList.containsKey(filename)) {
            String sha = targetList.get(filename);
            File targetFile = join(BLOB_DIR, sha);
            writeContents(join(CWD, filename), readContents(targetFile));
        } else {
            System.out.print("File does not exist in that commit.");
            System.exit(0);
        }
    }
    /** Checkout case 2: revert back any modification */
    public void checkout(String filename) {
        HashMap<String, String> currentList = currentCommit.getList();
        if (!currentList.containsKey(filename)) {
            System.out.print("File does not exist in that commit.");
            System.exit(0);
        }
        File targetFile = join(BLOB_DIR, currentList.get(filename));
        writeContents(join(CWD, filename),  readContents(targetFile));
    }

    // Unchecked
    /** Checkout case 3: goto the branch */
    public void checkoutBranch(String branchName) {
        untrackBuilder();
        if (!untrackedList.isEmpty()) {
            System.out.println("There is an untracked file in the way;"
                    + " delete it, or add and commit it first.");
            return;
        }
        if (branchName.equals(currentBranchName)) {
            System.out.print("No need to checkout the current branch.");
            return;
        }

        File checkTo = findBranch(branchName);
        /** can be fixed better (add compare) */
        for (String fileName : currentCommit.getList().keySet()) {
            join(CWD, fileName).delete();
        }
        currentCommit = readObject(join(GITLET_DIR, readContentsAsString(checkTo)), Commit.class);
        for (String fileName : currentCommit.getList().keySet()) {
            checkout(fileName);
        }
        currentBranchName = branchName;
        writeContents(CURRENT_BRANCH, currentBranchName);
        writeObject(CURRENT_COMMIT_SAVE, currentCommit);
    }

    public void branch(String branchName) {
        /** Store the Branch head Commit ID */
        File newBranch = join(BRANCH_DIR, branchName);
        /** Store the split history */
        File newBranchSplit = join(SPLIT_DIR, branchName);
        String splitPointID = currentCommit.getID();

        /** Update branch files */
        if (!newBranch.exists()) {
            writeContents(newBranch, splitPointID);
            if (branchName.equals("master")) {
                writeContents(newBranchSplit, splitPointID);
                return;
            }
            File trunkSplit = join(SPLIT_DIR, currentBranchName);
            String pastSplit = readContentsAsString(trunkSplit);
            String newSplit = (new StringBuilder())
                    .append(pastSplit).append(" ")
                    .append(splitPointID).append(" ")
                    .toString();
            writeContents(newBranchSplit, newSplit);
            writeContents(trunkSplit, newSplit);
        } else {
            System.out.print("A branch with that name already exists.");
        }
        return;
    }
    private File findMergeBranch(String branchName) {
        File targetFile = join(BRANCH_DIR, branchName);
        if (!targetFile.exists()) {
            System.out.print("A branch with that name does not exist.");
            System.exit(0);
        }
        return join(BRANCH_DIR, branchName);
    }

    /** Return the file that contains the branchHead */
    private File findBranch(String branchName) {
        File targetFile = join(BRANCH_DIR, branchName);
        if (!targetFile.exists()) {
            System.out.print("No such branch exists.");
            System.exit(0);
        }
        return join(BRANCH_DIR, branchName);
    }

    /** Return the file that contains the branchHead */
    private File findSplit(String branchName) {
        File targetFile = join(SPLIT_DIR, branchName);
        if (!targetFile.exists()) {
            System.out.print("No such branch exists.");
            System.exit(0);
        }
        return targetFile;
    }

    private Commit findBranchCommit(String branchName) {
        File target = findBranch(branchName);
        return readObject(join(GITLET_DIR, readContentsAsString(target)), Commit.class);
    }

    public void status() {
        untrackBuilder();
        System.out.println("=== Branches ===");
        System.out.print("*");
        System.out.println(readContentsAsString(CURRENT_BRANCH));
        otherBranchPrinter();
        System.out.println();

        System.out.println("=== Staged Files ===");
        stagePrinter(STAGE_DIR);
        System.out.println();

        System.out.println("=== Removed Files ===");
        stagePrinter(REMOVE_DIR);
        System.out.println();

        System.out.println("=== Modifications Not Staged For Commit ===");
        for (String i : deletedList) {
            System.out.println(i + " (deleted)");
        }
        for (String i : modifiedList) {
            System.out.println(i + " (modified)");
        }
        System.out.println();

        System.out.println("=== Untracked Files ===");
        for (String i : untrackedList) {
            System.out.println(i);
        }
        System.out.println();

    }
    private void otherBranchPrinter() {
        String currentBranchNamelocal = readContentsAsString(CURRENT_BRANCH);
        for (String i : plainFilenamesIn(BRANCH_DIR)) {
            if (i.equals(currentBranchNamelocal)) {
                continue;
            }
            System.out.println(i);
        }
    }
    private void stagePrinter(File dir) {
        for (String i : plainFilenamesIn(dir)) {
            System.out.println(i);
        }
    }

    private boolean compareFile(File a1, File a2) {
        byte[] a = readContents(a1);
        byte[] b = readContents(a2);
        return Arrays.equals(a, b);
    }

    private void branchBuilderHelper(Commit c) {
        commitHistory.put(c.getID(), c);
        if (c.getParent() == null) {
            return;
        }
        File parent = join(GITLET_DIR, c.getParent());
        branchBuilderHelper(readObject(parent, Commit.class));
    }

    private void branchBuilder() {
        branchBuilderHelper(currentCommit);
    }

    private void stageBuilder() {
        for (String i : plainFilenamesIn(STAGE_DIR)) {
            File stagedFile = join(STAGE_DIR, i);
            addStage.put(i, sha1(readContents(stagedFile)));
        }
    }

    private void removeStageBuilder() {
        for (String i : plainFilenamesIn(REMOVE_DIR)) {
            removeStage.add(i);
        }
    }

    public void merge(String branchName) {
        untrackBuilder();
        if (!untrackedList.isEmpty()) {
            System.out.println("There is an untracked file in the way;"
                    + " delete it, or add and commit it first.");
            return;
        }
        if (!addStage.isEmpty() | !removeStage.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return;
        }
        if (branchName.equals(currentBranchName)) {
            System.out.print("Cannot merge a branch with itself.");
            return;
        }
        findMergeBranch(branchName);

        Commit B = findBranchCommit(branchName);
        Commit S = findSplitCommit(branchName);
        Commit H = currentCommit;
        if (B.getID().equals(S.getID())) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        } else if (H.getID().equals(S.getID())) {
            System.out.println("Current branch fast-forwarded.");
            checkoutBranch(branchName);
            return;
        }
        Map<String, String> bCf = B.getList();
        Map<String, String> sCf = S.getList();
        Map<String, String> hCf = H.getList();
        Set<String> total = new HashSet<>();
        total.addAll(bCf.keySet());
        total.addAll(sCf.keySet());
        total.addAll(hCf.keySet());
        boolean isConflict = false;
        for (String i : total) {
            isConflict |= fileMerger(i, bCf, hCf, sCf);
        }
        if (isConflict) {
            System.out.println("Encountered a merge conflict.");
        }
        mergeCommit(currentBranchName, branchName, H.getID(), B.getID());
        updateHeadSplit(branchName, S.getID());
    }


    public Commit findSplitCommit(String branchName) {
        // currentBranchName
        String currentSplitHist = readContentsAsString(findSplit(currentBranchName));
        String branchSplitHist = readContentsAsString(findSplit(branchName));
        String[] csh = currentSplitHist.split(" ", 0);
        String[] bsh = branchSplitHist.split(" ", 0);

        HashSet<String> keyCheck = new HashSet<>();
        for (String i : bsh) {
            keyCheck.add(i);
        }
        int index = csh.length - 1;
        while (!keyCheck.contains(csh[index])) {
            index -= 1;
        }
        return readObject(join(GITLET_DIR, csh[index]), Commit.class);

//        int cap = Math.min(csh.length, bsh.length);
//        int i = 0;
//        String commonSplit = csh[0];
//        while (i < cap) {
//            if (csh[i].equals(bsh[i])) {
//                commonSplit = csh[i];
//                i++;
//            }
//        }
//        return readObject(join(GITLET_DIR, commonSplit), Commit.class);
    }

    public void updateHeadSplit(String branchName, String splitPoint) {
        // currentBranchName
        String currentSplitHist = readContentsAsString(findSplit(currentBranchName));
        String branchSplitHist = readContentsAsString(findSplit(branchName));
        String[] bsh = branchSplitHist.split(" ", 0);
//        LinkedList<String> csh_save = new LinkedList<>();
        String newSplit = currentSplitHist;
//        for (String i : csh) {
//            csh_save.addLast(i);
//        }
        boolean foundSpilt = false;
        for (String i : bsh) {
            foundSpilt |= (i.equals(splitPoint));
            if (i.equals(splitPoint)) {
                continue;
            }
            if (foundSpilt) {
                newSplit = (new StringBuilder()).append(i).append(" ").toString();
            }
        }
        writeContents(findSplit(currentBranchName), newSplit);
    }

    public void globalLog() {
        List<String> allCommits = plainFilenamesIn(GITLET_DIR);
        for (String i : allCommits) {
            File cif = join(GITLET_DIR, i);
            Commit ithCommit = readObject(cif, Commit.class);
            System.out.println("===");
            System.out.println("commit " + ithCommit.getID());
            if (ithCommit.isMerge()) {
                System.out.println(ithCommit.getParents());
            }
            System.out.println("Date: " + ithCommit.getTimestamp());
            System.out.println(ithCommit.getMessage());
            System.out.println();
        }
    }

    public void find(String cm) {
        List<String> allCommits = plainFilenamesIn(GITLET_DIR);
        int count = 0;
        for (String i : allCommits) {
            File cif = join(GITLET_DIR, i);
            Commit ithCommit = readObject(cif, Commit.class);
            if (ithCommit.getMessage().equals(cm)) {
                System.out.println(ithCommit.getID());
                count++;
            }
        }
        if (count == 0) {
            System.out.print("Found no commit with that message.");
            return;
        }
    }

    /** Helper function */
    /** update stage and removeStage */
    private boolean fileMerger(String filename,
                            Map<String, String> commitB,
                            Map<String, String> commitH,
                            Map<String, String> commitS) {
        String H = commitH.get(filename);
        String B = commitB.get(filename);
        String S = commitS.get(filename);
        if (S == null) {
            if (H == null && B != null) {
                makeFile(filename, B);
                add(filename);
            } else if (B == null && H != null) {
                return false;
            }
        }
        if (S != null) {
            if (H == null && B == null) {
                return false;
            } else if (H == null && S.equals(B)) {
                return false;
            } else if (B == null && S.equals(H)) {
                rm(filename);
            } else if (S.equals(B) && !S.equals(H)) {
                makeFile(filename, H);
                add(filename);
            } else if (S.equals(H) && !S.equals(B)) {
                makeFile(filename, B);
                add(filename);
            } else if (H.equals(B)) {
                return false;
            } else if (!H.equals(B)) {
                conflict(filename, B);
                add(filename);
                return true;
            }
        }
        return false;
    }

    private void makeFile(String fileName, String sha) {
        File outFile = join(CWD, fileName);
        File inFile = join(BLOB_DIR, sha);
        byte[] content = readContents(inFile);
        writeContents(outFile, content);
    }

    public void removeBranch(String branchName) {
        if (branchName.equals(currentBranchName)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }
        File db = join(BRANCH_DIR, branchName);
        if (!db.exists()) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        db.delete();
    }


    private String findID(String sID) {
        int len = sID.length();
        HashSet<String> allID = new HashSet<>();
        allID.addAll(plainFilenamesIn(GITLET_DIR));
        for (String i : allID) {
            if (i.substring(0, len).equals(sID)) {
                return i;
            }
        }
        System.out.print("No commit with that id exists.");
        return "fail";
    }
    public void reset(String shaCode) {
        untrackBuilder();
        if (!untrackedList.isEmpty()) {
            System.out.println("There is an untracked file in the way;"
                    + " delete it, or add and commit it first.");
            return;
        }
        File targetFile = join(GITLET_DIR, shaCode);

        if (!targetFile.exists()) {
            System.out.print("No commit with that id exists.");
            return;
        }
        currentCommit = readObject(targetFile, Commit.class);
        headPos = currentCommit.getID();
        for (String i : plainFilenamesIn(CWD)) {
            restrictedDelete(i);
        }
        for (String i : currentCommit.getList().keySet()) {
            checkout(i);
        }
        writeObject(CURRENT_COMMIT_SAVE, currentCommit);
        /** Clear Stage, only needs to remove the temp Files in stage */
        for (String i : plainFilenamesIn(STAGE_DIR)) {
            File delFile = join(STAGE_DIR, i);
            delFile.delete();
        }
        /** Clear removeStage */
        for (String i : plainFilenamesIn(REMOVE_DIR)) {
            File delFile = join(REMOVE_DIR, i);
            delFile.delete();
        }
        /** Update current Branch Head */
        File branchFile = findBranch(readContentsAsString(CURRENT_BRANCH));
        writeContents(branchFile, headPos);
    }

    private void conflict(String fileName, String sha) {
        String contentHead;
        String contentBranch;
        File outFile = join(CWD, fileName);
        File inFile;
        if (sha != null) {
            inFile = join(BLOB_DIR, sha);
            contentBranch = readContentsAsString(inFile);
        } else {
            contentBranch = "";
        }
        if (outFile.exists()) {
            contentHead = readContentsAsString(outFile);
        } else {
            contentHead = "";
        }
        String newSplit = (new StringBuilder())
                .append("<<<<<<< HEAD")
                .append("\n")
                .append(contentHead)
                .append("=======")
                .append("\n")
                .append(contentBranch)
                .append(">>>>>>>")
                .append("\n")
                .toString();
//        System.out.println(newSplit);
        writeContents(outFile, newSplit);
    }

    private LinkedList<String> untrackedList;
    private LinkedList<String> modifiedList;
    private LinkedList<String> deletedList;

    private void untrackBuilder() {
        untrackedList = new LinkedList<>();
        modifiedList = new LinkedList<>();
        deletedList = new LinkedList<>();
        HashSet<String> total = new HashSet<>();
        for (String i : plainFilenamesIn(CWD)) {
            total.add(i);
        }
        total.addAll(currentCommit.getList().keySet());
        for (String j : total) {
            switch (untrackChecker(j)) {
                case "untrack":
                    untrackedList.add(j);
                    break;
                case "deleted":
                    deletedList.add(j);
                    break;
                case "modified":
                    modifiedList.add(j);
                    break;
                case "tracked":
                    break;
                default:
                    break;
            }
        }
    }



    private String untrackChecker(String fileName) {
        File i = join(CWD, fileName);
        HashSet<String> sysFile = new HashSet<>();
        sysFile.add("log");
        sysFile.add("gitlet-design.md");
        sysFile.add("Makefile");
        sysFile.add("pom.xml");
        sysFile.add("proj2.iml");
        if (!sysFile.contains(fileName) && !addStage.keySet().contains(fileName)) {
            // Untracked
            if (!currentCommit.getList().keySet().contains(fileName)
                    | (i.exists() && removeStage.contains(fileName))) {
                return "untrack";
            } else if (currentCommit.getList().keySet().contains(fileName)
                    && !i.exists() && !removeStage.contains(fileName)) {
                return "deleted";
            } else if (currentCommit.getList().keySet().contains(fileName)
                    && i.exists()) {
                File oldVersion = join(BLOB_DIR, currentCommit.getList().get(fileName));
                if (!compareFile(oldVersion, i)) {
                    return "modified";
                }
            }
        }
        return "tracked";
    }
}
