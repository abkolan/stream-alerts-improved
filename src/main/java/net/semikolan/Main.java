package net.semikolan;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ExecutionException;



public class Main {

  private static int ALERT_NUM_OCCURRENCES = 5000000;
  private static int TIME_INTERVAL_MINUTES = 5;

  private final static int INTERVAL = TIME_INTERVAL_MINUTES * 60;

  private static final HashMap<String, long[]> wordMap = new HashMap<>();

  private static boolean considerStopWords = false;

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    for (String arg : args) {
      System.out.println(arg);
    }
    if (args.length == 1 && args[0].equals("test")) {
   // if (true) {
      driver();
    } else {
      if (args.length == 3) {
        ALERT_NUM_OCCURRENCES = Integer.parseInt(args[0]);
        TIME_INTERVAL_MINUTES = Integer.parseInt(args[1]);
        considerStopWords = Boolean.parseBoolean(args[2]);
      }
      // Reading the stream from STDOUT, used to pipe random tweets
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader bufReader = new BufferedReader(isReader);
      String inputStr = "";
      long epoch = 0;
      while (inputStr != null) {
        try {
          inputStr = bufReader.readLine();
          epoch = System.currentTimeMillis();
        } catch (IOException e) {
          e.printStackTrace();
        }
        if (inputStr != null) {
          process(inputStr, epoch);
        } else {
          System.out.println("<empty>");
        }
      }
    }
  }

  //A Test driver
  private static void driver() throws ExecutionException, InterruptedException {
    System.out.println("Test mode");
    ALERT_NUM_OCCURRENCES = 5;
    TIME_INTERVAL_MINUTES = 1;
    System.out.println("Sending Mock Data");
    process("When you sense there’s something more", System.currentTimeMillis());
    Thread.sleep(1000);
    process("when the life that used to satisfy you no longer seems enough", System.currentTimeMillis());
    Thread.sleep(2000);
    process("when security seems suffocating and pleasures lose their taste", System.currentTimeMillis());
    Thread.sleep(3000);
    process("when dreams of success can’t motivate you anymore", System.currentTimeMillis());
    Thread.sleep(500);
    process("When you ache with a sadness that doesn’t seem to have a source", System.currentTimeMillis());
    process("when you feel a hunger you’ve never known before", System.currentTimeMillis());
    Thread.sleep(1000);

    //Test case for within in window
    process("qxzy",System.currentTimeMillis());
    Thread.sleep(1000);
    process("qxzy",System.currentTimeMillis());
    Thread.sleep(1000);
    process("qxzy",System.currentTimeMillis());
    Thread.sleep(1000);
    process("qxzy",System.currentTimeMillis());
    Thread.sleep(56 * 1000);
    process("qxzy",System.currentTimeMillis());
    System.out.println("DONE");
    Thread.sleep(5 * 1000);
  }

  private static void process(String inputStr, long epoch) throws ExecutionException {
    inputStr = inputStr.toLowerCase();
    StringTokenizer tokenizer = new StringTokenizer(inputStr, " \t\r\n.?!\",");
    while (tokenizer.hasMoreElements()) {
      String word = tokenizer.nextElement().toString();
      if (considerStopWords) {
        if (!word.isEmpty() && !stopWords.contains(word) && !word.startsWith("@")) {
          eval(word, epoch);
        }
      } else {
        if (!word.isEmpty() && !word.startsWith("@")) {
          eval(word, epoch);
        }
      }
    }
  }

  private static void eval(String word, long epoch) throws ExecutionException {
    //Check wordMap contains this word if it doesn't initialize one
    long[] tree;
    if (wordMap.containsKey(word)) {
      tree = wordMap.get(word);
    } else {
      tree = initTreeForSize(INTERVAL);
      wordMap.put(word, tree);
    }

    //Extract seconds from epoch
    long seconds = epoch / 1000; //Extract seconds
    int bucket = (int) seconds % INTERVAL; //Get the bucket for this interval

    //Check the last updated ts
    long lastUpdatedTS = tree[tree.length - 1];

    if (lastUpdatedTS == 0) {
      // never updated, word seen for the first time
      // SegTree set
      setRange(tree, INTERVAL, bucket, bucket, 1);
    } else if (lastUpdatedTS == seconds) {
      // value has to be bumped up for the same
      incrRange(tree, INTERVAL, bucket, bucket, 1);
    } else if ((int) (seconds - lastUpdatedTS) >= INTERVAL) {
      //reset all values older than the interval to 0
      setRange(tree, INTERVAL, 0, INTERVAL - 1, 0); //TODO: Optimize this

      //set the current interval bucket to 1
      setRange(tree, INTERVAL, bucket, bucket, 1);
    } else if ((int) (seconds - lastUpdatedTS) == 1) {
      //Value is immediately after the previous timestamp, no updations needed
      // SegTree set
      setRange(tree, INTERVAL, bucket, bucket, 1);
    } else { //currentTS is within the interval but there are gaps
      int lastUpdatedBucket = (int) lastUpdatedTS % INTERVAL;

      //Reset values from lastUpdatedBucket to current bucket
      if (lastUpdatedBucket > bucket) {
        //Circular update needed

        // Part 1 lastBucket till the end
        // SegTree set range
        setRange(tree, INTERVAL, lastUpdatedBucket + 1, INTERVAL - 1, 0);

        // Part 2 0 to current bucket
        // SegTree set range
        setRange(tree, INTERVAL, 0, bucket - 1, 0);
      } else {
        // SegTree set range
        setRange(tree, INTERVAL, lastUpdatedBucket + 1, bucket - 1, 0);
      }
      // Set current value
      // SegTree set
      setRange(tree, INTERVAL, bucket, bucket, 1);
    }

    //update lastUpdatedTS
    tree[tree.length - 1] = seconds;


    //Compute the sum of the Window counter
    int sum = (int) getSum(tree, INTERVAL, 0, INTERVAL - 1);
    if (sum >= ALERT_NUM_OCCURRENCES) {
      System.out.printf("ALERT generated for word = %s\n", word);
    }
  }


  private static void incrRange(long tree[], int n, int us, int ue, int val) {
    incrRangeUtil(tree, 0, 0, n - 1, us, ue, val);
  }

  private static void incrRangeUtil(long[] tree, int si, int ss, int se, int us,
                                    int ue, int val) {
    // out of range
    if (ss > se || ss > ue || se < us)
      return;

    // Current node is a leaf node
    if (ss == se) {
      // Increment by val to current node
      tree[si] += val;
      return;
    }

    // If not a leaf node, increment for this node's children.
    int mid = (ss + se) / 2;
    incrRangeUtil(tree, si * 2 + 1, ss, mid, us, ue, val);
    incrRangeUtil(tree, si * 2 + 2, mid + 1, se, us, ue, val);

    // Use the result of children calls to compute this node's sum
    tree[si] = tree[si * 2 + 1] + tree[si * 2 + 2];
  }

  private static void setRange(long[] tree, int n, int us, int ue, int value) {
    setRangeUtil(tree, 0, 0, n - 1, us, ue, value);
  }

  private static void setRangeUtil(long[] tree, int si, int ss, int se, int us, int ue, int value) {
    // out of range
    if (ss > se || ss > ue || se < us)
      return;

    // Current node is a leaf node
    if (ss == se) {
      // Set the value to the current node
      tree[si] = value;
      return;
    }

    // If not a leaf node, set for this node's children.
    int mid = (ss + se) / 2;
    setRangeUtil(tree, si * 2 + 1, ss, mid, us, ue, value);
    setRangeUtil(tree, si * 2 + 2, mid + 1, se, us, ue, value);

    // Use the result of children calls to update this node's sum
    tree[si] = tree[si * 2 + 1] + tree[si * 2 + 2];
  }

  private static long[] initTreeForSize(int n) {
    //Compute Height of the Segment tree
    int x = (int) (Math.ceil(Math.log(n) / Math.log(2)));

    //Compute the max height of the Segtree
    int max = 2 * (int) Math.pow(2, x) - 1;

    //Allocate a new array for max+1
    //The last index is used to store the last updated timestamp
    long[] tree = new long[max + 1];
    return tree;
  }

  private static long getSum(long[] tree, int n, int qs, int qe) {
    // Check for bounds
    if (qs < 0 || qe > n - 1 || qs > qe) {
      System.out.println("Out of Bounds");
      //TODO: Raise exception
      return -1;
    }
    return getSumUtil(tree, 0, n - 1, qs, qe, 0);
  }

  private static long getSumUtil(long[] tree, int ss, int se, int qs, int qe, int si) {
    // If segment of this node is a part of given range, then return
    // the sum of the segment
    if (qs <= ss && qe >= se)
      return tree[si];

    // If segment of this node is outside the given range
    if (se < qs || ss > qe)
      return 0;

    // If a part of this segment overlaps with the given range
    int mid = getMid(ss, se);
    return getSumUtil(tree, ss, mid, qs, qe, 2 * si + 1) +
        getSumUtil(tree, mid + 1, se, qs, qe, 2 * si + 2);
  }

  private static int getMid(int s, int e) {
    return s + (e - s) / 2;
  }


  //NLTK's list of english stopwords
  private static String[] stopWordsEnglish = {
      "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours", "yourself", "yourselves",
      "he", "him", "his", "himself", "she", "her", "hers", "herself", "it", "its", "itself", "they", "them", "their",
      "theirs", "themselves", "what", "which", "who", "whom", "this", "that", "these", "those", "am", "is", "are",
      "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does", "did", "doing", "a", "an",
      "the", "and", "but", "if", "or", "because", "as", "until", "while", "of", "at", "by", "for", "with", "about",
      "against", "between", "into", "through", "during", "before", "after", "above", "below", "to", "from", "up",
      "down", "in", "out", "on", "off", "over", "under", "again", "further", "then", "once", "here", "there", "when",
      "where", "why", "how", "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor",
      "not", "only", "own", "same", "so", "than", "too", "very", "s", "t", "can", "will", "just", "don't", "should",
      "now", "i'm", "i", "me"
  };
  private static final Set<String> stopWords = new HashSet<>(Arrays.asList(stopWordsEnglish));


}
