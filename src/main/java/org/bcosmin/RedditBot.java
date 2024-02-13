package org.bcosmin;

import net.dean.jraw.ApiException;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.NetworkAdapter;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.OkHttpNetworkAdapter;
import net.dean.jraw.models.*;
import net.dean.jraw.oauth.Credentials;
import net.dean.jraw.oauth.OAuthException;
import net.dean.jraw.oauth.OAuthHelper;
import net.dean.jraw.http.UserAgent;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import net.dean.jraw.pagination.SearchPaginator;
import net.dean.jraw.references.SubredditReference;
import net.dean.jraw.tree.CommentNode;
import net.dean.jraw.tree.RootCommentNode;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public class RedditBot {
    public static void main(String[] args) {
        Properties props = new Properties();
        try {
            FileInputStream in = new FileInputStream("credentials.properties");
            props.load(in);
            in.close();
        } catch (IOException e) {
            System.out.println("Error reading from properties file");
            e.printStackTrace();
            return;
        }

        // Create a RedditClient instance
        UserAgent userAgent = new UserAgent("bot", props.getProperty("appName"), "v0.1", props.getProperty("username"));
        NetworkAdapter adapter = new OkHttpNetworkAdapter(userAgent);
        RedditClient reddit;
        try {
            reddit = OAuthHelper.automatic(adapter, Credentials.script(
                    props.getProperty("username"),
                    props.getProperty("password"),
                    props.getProperty("clientID"),
                    props.getProperty("clientSecret")
            ));
        } catch (OAuthException e) {
            System.out.println("Invalid Reddit credentials");
            e.printStackTrace();
            return;
        }

        // Define the subreddit
        String subreddit = "R/programare";
        SubredditReference subredditRef = reddit.subreddit(subreddit);

        // Fetch and process posts in batches
        List<Submission> newPosts;
        try {
            newPosts = subredditRef.posts().limit(10).build().next();
        } catch (NetworkException e) {
            System.out.println("Failed to fetch the posts");
            e.printStackTrace();
            return;
        }

        for (Submission newPost : newPosts) {
            // Check if a user commented "!findSimilarPosts"
            boolean commandFound = false;
            RootCommentNode root = reddit.submission(newPost.getId()).comments();
            Iterator<CommentNode<PublicContribution<?>>> it = root.walkTree().iterator();
            while (it.hasNext()) {
                PublicContribution<?> thing = it.next().getSubject();
                if (thing instanceof Comment comment) {
                    if (comment.getBody().equals("!findSimilarPosts")) {
                        commandFound = true;
                        break;
                    }
                }
            }

            if (!commandFound) {
                continue;
            }

            // Extract keywords from the new post
            String keywords = extractKeywords(newPost);

            // Check if keywords are empty
            if (keywords.isEmpty()) {
                System.out.println("No keywords extracted from the post");
                continue;
            }

            // Search for similar posts
            SearchPaginator paginator = reddit.subreddit(subreddit).search().query(keywords).build();

            // Post the links to the most relevant similar posts
            for (Submission similarPost : paginator.next()) {
                try {
                    reddit.submission(newPost.getId()).reply("Similar post: " + similarPost.getUrl());
                } catch (ApiException e) {
                    System.out.println("Failed to post the reply");
                    e.printStackTrace();
                }
            }
        }
    }

    private static String extractKeywords(Submission post) {
        List<String> result = new ArrayList<>();
        try {
            Analyzer analyzer = new RomanianAnalyzer();
            String text = post.getTitle() + " " + post.getSelfText();
            TokenStream stream = analyzer.tokenStream(null, new StringReader(text));
            stream.reset();
            while (stream.incrementToken()) {
                result.add(stream.getAttribute(CharTermAttribute.class).toString());
            }
            stream.end();
            stream.close();
        } catch (IOException e) {
            System.out.println("Failed to analyze the post title and content");
            e.printStackTrace();
        }
        return String.join(" ", result);
    }
}