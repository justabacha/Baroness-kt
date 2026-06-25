package com.baroness.app.data

import com.baroness.app.models.VibeQuote

object SignatureLoops {
    private const val BASE_URL = "https://baroness-test.vercel.app"

    val quotes = listOf(
        VibeQuote(
            part1 = "Some people just make the world easier.", //Baroness Quote
            part2 = "You're exactly where you need to be.", //My Quote
            photo1 = "$BASE_URL/bucket/Image-41.jpg",
            photo2 = "$BASE_URL/bucket/Image-42.jpg"
        ),
        VibeQuote(
            part1 = "I don't really do 'ordinary'.",
            part2 = "We're on a completely different wave.",
            photo1 = "$BASE_URL/bucket/Image-43.jpg",
            photo2 = "$BASE_URL/bucket/Image-44.jpg"
        ),
        VibeQuote(
            part1 = "Everything feels a lot quieter now.",
            part2 = "I finally found a place that feels like home.",
            photo1 = "$BASE_URL/bucket/Image-70.jpg",
            photo2 = "$BASE_URL/bucket/Image-69.jpg"
        ),
        /*VibeQuote(
            part1 = "admire me and inspire me",
            part2 = "I do the same thing for that's the irony",
            photo1 = "$BASE_URL/bucket/Image-1.jpg",
            photo2 = "$BASE_URL/bucket/Image-2.jpg"
        ),
        VibeQuote(
            part1 = "You My Fighter U Mah Best friend",
            part2 = "its an understatement calling u mah girlfriend :)",
            photo1 = "$BASE_URL/bucket/Image-3.jpg",
            photo2 = "$BASE_URL/bucket/Image-4.jpg"
        ),
        VibeQuote(
            part1 = "You always make me feel butterflies when u come around :)",
            part2 = "you make me know LOVE is not the answer",
            photo1 = "$BASE_URL/bucket/Image-5.jpg",
            photo2 = "$BASE_URL/bucket/Image-6.jpg"
        ),
        VibeQuote(
            part1 = "am lucky to have a best friend like you",
            part2 = "always better together :)",
            photo1 = "$BASE_URL/bucket/Image-7.jpg",
            photo2 = "$BASE_URL/bucket/Image-8.jpg"
        ),
        VibeQuote(
            part1 = "Life is hard, but at least I have a pretty best-friend",
            part2 = "I got the prettiest bestie :)",
            photo1 = "$BASE_URL/bucket/Image-9.jpg",
            photo2 = "$BASE_URL/bucket/Image-10.jpg"
        ),
        VibeQuote(
            part1 = "Best Friend Forever",
            part2 = "2.2.4 yeah Today, Tomorrow and Forever",
            photo1 = "$BASE_URL/bucket/Image-11.jpg",
            photo2 = "$BASE_URL/bucket/Image-12.jpg"
        ),
        VibeQuote(
            part1 = "Best friends are hard to find :(",
            part2 = "The Best one is already MINE :)",
            photo1 = "$BASE_URL/bucket/Image-13.jpg",
            photo2 = "$BASE_URL/bucket/Image-14.jpg"
        ),
        VibeQuote(
            part1 = "We are perfect match :)",
            part2 = "We go together like copy and paste",
            photo1 = "$BASE_URL/bucket/Image-15.jpg",
            photo2 = "BASE_URL/bucket/Image-16.jpg"
        ),
        VibeQuote(
            part1 = "Dear Best friend, I Love You 😉",
            part2 = "I like you because you're wierd like me 😂",
            photo1 = "$BASE_URL/bucket/Image-17.jpg",
            photo2 = "$BASE_URL/bucket/Image-18.jpg"
        ),
        VibeQuote(
            part1 = "some friends are not just friends they are soulmate",
            part2 = "that 'grow together' type friendship",
            photo1 = "$BASE_URL/bucket/Image-19.jpg",
            photo2 = "$BASE_URL/bucket/Image-20.jpg"
        ),
        VibeQuote(
            part1 = "I don't know what I did to deserve a best friend like you",
            part2 = "but I'm so grateful to have you in my life",
            photo1 = "$BASE_URL/bucket/Image-21.jpg",
            photo2 = "$BASE_URL/bucket/Image-22.jpg"
        ),
        VibeQuote(
            part1 = "some connection are priceless ✌️",
            part2 = "They hate us 'cause they ain't us😎",
            photo1 = "$BASE_URL/bucket/Image-23.jpg",
            photo2 = "$BASE_URL/bucket/Image-24.jpg"
        ),
        VibeQuote(
            part1 = "sometimes u just need to do bad things with good friends",
            part2 = "The best memories comes from the bad ideas done with best friends",
            photo1 = "$BASE_URL/bucket/Image-25.jpg",
            photo2 = "$BASE_URL/bucket/Image-26.jpg"
        ),
       VibeQuote(
           part1 = "..but you're my best friend & I can't lose you :(",
           part2 = "Thank You for being my unpaid therapist🙂‍↕️",
           photo1 = "$BASE_URL/bucket/Image-27.jpg",
           photo2 = "$BASE_URL/bucket/Image-28.jpg"
       ),
        VibeQuote(
            part1 = "Souls don't meet by accident..❤️!",
            part2 = "I Love You, idiot.✿..!",
            photo1 = "$BASE_URL/bucket/Image-29.jpg",
            photo2 = "$BASE_URL/bucket/Image-30.jpg"
        ),
        VibeQuote(
            part1 = "Cheers to our FRIEND-CHIP!🥂",
            part2 = "u'm.?? !t's forever kind thing :)",
            photo1 = "$BASE_URL/bucket/Image-31.jpg",
            photo2 = "$BASE_URL/bucket/Image-32.jpg"
        ),
        VibeQuote(
            part1 = "Every Tall friend need a short best friend🥴",
            part2 = "you SHORT!!, I'm literally older than you😂",
            photo1 = "$BASE_URL/bucket/Image-33.jpg",
            photo2 = "$BASE_URL/bucket/Image-34.jpg"
        ),
        VibeQuote(
            part1 = "..and till the END you're stuck with me",
            part2 = "I'll always be there to irritate you",
            photo1 = "$BASE_URL/bucket/Image-35.jpg",
            photo2 = "$BASE_URL/bucket/Image-36.jpg"
        ),
        VibeQuote(
           part1 = "for the highs and lows and moments between",
           part2 = "the right people stay<3",
           photo1 = "$BASE_URL/bucket/Image-37.jpg",
           photo2 = "$BASE_URL/bucket/Image-38.jpg"
        ),
        VibeQuote(
           part1 = "Thank You For Existing",
           part2 = "My Favourite Human ❤️",
           photo1 = "$BASE_URL/bucket/Image-39.jpg",
           photo2 = "$BASE_URL/bucket/Image-40.jpg"
        ),
        VibeQuote(
           part1 = "...made of Memories ˘⌣˘",
           part2 = "F.R.I.E.N.D.S \n have less, but the best",
           photo1 = "$BASE_URL/bucket/Image-41.jpg",
           photo2 = "$BASE_URL/bucket/Image-42.jpg"
        ),
        VibeQuote(
           part1 = "11:11 \n You are My Wish :)",
           part2 = "☆☆..counting my lucky stars",
           photo1 = "$BASE_URL/bucket/Image-43.jpg",
           photo2 = "$BASE_URL/bucket/Image-44.jpg"
        ),
        VibeQuote(
           part1 = "you are so WEIRD (Don't Change)😂",
           part2 = "you're wierd I like that 😉",
           photo1 = "$BASE_URL/bucket/Image-45.jpg",
           photo2 = "$BASE_URL/bucket/Image-46.jpg"
        ),
        VibeQuote(
           part1 = "my rose-colored boy \n Thank You for being the Best Friend I could ask for❤️",
           part2 = "my dearest \n I Hope you Always find a Reason to SMILE :)",
           photo1 = "$BASE_URL/bucket/Image-47.jpg",
           photo2 = "$BASE_URL/bucket/Image-48.jpg"
        ),
        VibeQuote(
            part1 = "You Complete ME<3",
            part2 = "we'd look cute together..!",
            photo1 = "$BASE_URL/bucket/Image-49.jpg",
            photo2 = "$BASE_URL/bucket/Image-50.jpg"
        )*/

    )
}