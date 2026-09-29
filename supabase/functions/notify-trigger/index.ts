import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import { GoogleAuth } from "https://esm.sh/google-auth-library@8.7.0"

// FCM HTTP v1 API URL
const FCM_URL = "https://fcm.googleapis.com/v1/projects/"

serve(async (req) => {
  // Handle preflight requests
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: { 'Access-Control-Allow-Origin': '*' } })
  }

  try {
    // 1. Parse payload from database trigger
    const { record, event, notification_type } = await req.json()
    console.log(`Processing ${event} (type=${notification_type ?? 'wishlist'}):`, record)

    if (event !== 'INSERT') {
      return new Response('Event ignored', { status: 200 })
    }

    const creatorId = record.creator_id
    const messageText = record.text
    const isFridayProactive = notification_type === 'friday_proactive' || creatorId === 'friday_official'

    // 2. Initialize Supabase client
    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    // 3. Determine recipient
    // For FRIDAY proactive check-ins, record.owner_id is the target user.
    // For Wishlist items, recipient is the other persona ('phesty_official' <-> 'baroness_official').
    const recipientId = isFridayProactive
      ? (record.owner_id ?? (creatorId === 'phesty_official' ? 'baroness_official' : 'phesty_official'))
      : (creatorId === 'phesty_official' ? 'baroness_official' : 'phesty_official')

    console.log(`Recipient identified: ${recipientId} (isFridayProactive=${isFridayProactive})`)

    // 4. Fetch recipient's FCM token
    const { data: profile, error: profileError } = await supabase
      .from('profiles')
      .select('fcm_token, display_name')
      .eq('id', recipientId)
      .single()

    if (profileError || !profile?.fcm_token) {
      console.error('Recipient token not found or profile error:', profileError)
      return new Response('Recipient token not found', { status: 200 })
    }

    // 5. Build payload fields according to notification type
    let notificationTitle: string
    let route: string
    let featureType: string
    let channelId: string
    let avatarUrl: string

    if (isFridayProactive) {
      notificationTitle = 'FRIDAY'
      route = 'Friday'
      featureType = 'messages'
      channelId = 'messages_notifications'
      avatarUrl = 'https://img.icons8.com/fluency/48/artificial-intelligence.png'
    } else {
      // Wishlist notification path (Unchanged)
      const { data: sender } = await supabase
        .from('profiles')
        .select('display_name, avatar_url')
        .eq('id', creatorId)
        .single()

      const senderName = sender?.display_name ?? 'Someone'
      notificationTitle = `${senderName} added a new wish`
      route = 'Wishlist'
      featureType = 'wishlist'
      channelId = 'wishlist_notifications'
      avatarUrl = sender?.avatar_url ?? ''
    }

    // 6. Generate OAuth2 token for FCM using service account credentials
    const projectId = Deno.env.get('FCM_PROJECT_ID')
    const clientEmail = Deno.env.get('FCM_CLIENT_EMAIL')
    const privateKey = Deno.env.get('FCM_PRIVATE_KEY')?.replace(/\\n/g, '\n')

    if (!projectId || !clientEmail || !privateKey) {
      throw new Error('Missing FCM configuration secrets')
    }

    const auth = new GoogleAuth({
      credentials: {
        client_email: clientEmail,
        private_key: privateKey,
      },
      scopes: ['https://www.googleapis.com/auth/firebase.messaging'],
    })

    const accessToken = await auth.getAccessToken()

    // 7. Send notification via FCM v1 API
    console.log(`Sending FCM notification (${channelId}) to token: ${profile.fcm_token.substring(0, 10)}...`)
    const response = await fetch(`${FCM_URL}${projectId}/messages:send`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        message: {
          token: profile.fcm_token,
          notification: {
            title: notificationTitle,
            body: messageText,
          },
          data: {
            route: route,
            feature_type: featureType,
            avatar_url: avatarUrl,
          },
          android: {
            priority: 'high',
            notification: {
              channel_id: channelId,
              icon: 'ic_notification',
              color: '#6200EE',
            },
          },
        },
      }),
    })

    const result = await response.json()
    if (response.status !== 200) {
      console.error('FCM API Error:', result)
    } else {
      console.log('FCM Success:', result)
    }

    return new Response(JSON.stringify(result), {
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
      status: response.status,
    })

  } catch (error) {
    console.error('Function error:', error)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
      status: 500,
    })
  }
})
