package org.trypticon.megahal.engine;

import java.util.*;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;

/**
 * Main class implementing the main MegaHAL engine.
 * Provides methods to train the brain, and to generate text
 * responses from it.
 *
 * @author Trejkaz
 */
public class MegaHAL {

    // Fixed word sets.
    private final Map<Symbol, Symbol> swapWords;     // A mapping of words which will be swapped for other words.

    // Hidden Markov first_attempt.Model
    private final Model model;

    // Parsing utilities
    private final Splitter splitter;

    // Random Number Generator
    private final Random rng = new Random();

    /**
     * Constructs the engine, reading the configuration from the data directory.
     *
     * @throws IOException if an error occurs reading the configuration.
     */
    public MegaHAL() throws IOException {
        /*
         * 0. Initialise. Add the special "<BEGIN>" and "<END>" symbols to the
         * dictionary. Ex: 0:"<BEGIN>", 1:"<END>"
         *
         * NOTE: Currently debating the need for a dictionary.
         */
        //dictionary.add("<BEGIN>");
        //dictionary.add("<END>");

        swapWords = Utils.readSymbolMapFromFile("data/megahal.swp");
        Set<Symbol> banWords = Utils.readSymbolSetFromFile("data/megahal.ban");
        Set<Symbol> auxWords = Utils.readSymbolSetFromFile("data/megahal.aux");
        // TODO: Implement first message to user (formulateGreeting()?)
        Set<Symbol> greetWords = Utils.readSymbolSetFromFile("data/megahal.grt");
        SymbolFactory symbolFactory = new SymbolFactory(new SimpleKeywordChecker(banWords, auxWords));
        splitter = new WordNonwordSplitter(symbolFactory);

        model = new Model();

        BufferedReader reader = new BufferedReader(new FileReader("data/megahal.trn"));
        String line;
        int trainCount = 0;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }
            if (line.charAt(0) == '#') {
                continue;
            }
            trainOnly(line);
            trainCount++;
        }
        reader.close();
        System.out.println("Trained with " + trainCount + " sentences.");
    }

    /**
     * Trains on a single line of text.
     *
     * @param userText the line of text.
     */
    public void trainOnly(String userText) {
        // Split the user's line into symbols.
        List<Symbol> userWords = splitter.split(userText.toUpperCase());

        // Train the brain from the user's list of symbols.
        model.train(userWords);
    }

    /**
     * Formulates a line back to the user, and also trains from the user's text.
     *
     * @param userText the line of text.
     * @return the reply.
     */
    public String formulateReply(String userText) {

        // Split the user's line into symbols.
        List<Symbol> userWords = splitter.split(userText.toUpperCase());

        // Train the brain from the user's list of symbols.
        model.train(userWords);

        // Find keywords in the user's input.
        List<Symbol> userKeywords = new ArrayList<Symbol>(userWords.size());
        for (Symbol s : userWords) {
            if (s.isKeyword()) {
                Symbol swap = swapWords.get(s);
                if (swap != null) {
                    s = swap;
                }
                userKeywords.add(s);
            }
        }

        // Generate candidate replies.
        //int candidateCount = 0;
        double bestInfoContent = 0.0;
        List<Symbol> bestReply = null;
        int timeToTake = 1000 * 5; // 5 seconds.
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < timeToTake) {
            //System.out.print("Generating... ");
            List<Symbol> candidateReply = model.generateRandomSymbols(rng, userKeywords);
            //candidateCount++;
            //System.out.println("Candidate: " + candidateReply);

            double infoContent = model.calculateInformation(candidateReply, userKeywords);
            //System.out.println("infoContent="+infoContent);
            if (infoContent > bestInfoContent && !Utils.equals(candidateReply, userWords)) {
                bestInfoContent = infoContent;
                bestReply = candidateReply;
            }
        }
        //System.out.println("Candidates generated: " + candidateCount);
        //System.out.println("Best reply generated: " + bestReply);

        // Return the generated string, tacked back together.
        return (bestReply == null) ? null : splitter.join(bestReply);
    }
}
