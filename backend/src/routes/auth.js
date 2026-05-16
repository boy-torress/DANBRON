const express = require('express');
const jwt = require('jsonwebtoken');
const { supabase } = require('../db');
const { v4: uuidv4 } = require('uuid');
const { assertEmail, sanitizeName } = require('../utils/validation');

const router = express.Router();

// Register or login user
router.post('/auth', async (req, res) => {
  try {
    const { email, name } = req.body;

    if (!email) {
      return res.status(400).json({ error: 'Email required' });
    }
    const cleanEmail = assertEmail(email);
    const cleanName = sanitizeName(name, cleanEmail.split('@')[0]);

    // Try to find user
    let { data: user, error } = await supabase
      .from('users')
      .select('*')
      .eq('email', cleanEmail)
      .single();

    // If user doesn't exist, create one
    if (error && error.code === 'PGRST116') {
      const { data: newUser, error: createError } = await supabase
        .from('users')
        .insert({
          id: uuidv4(),
          email: cleanEmail,
          name: cleanName,
          created_at: new Date().toISOString()
        })
        .select()
        .single();

      if (createError) throw createError;
      user = newUser;
    } else if (error) {
      throw error;
    }

    // Generate JWT token
    const token = jwt.sign(
      { userId: user.id, email: user.email },
      process.env.JWT_SECRET,
      { expiresIn: process.env.JWT_EXPIRES_IN || '30d' }
    );

    res.json({
      token,
      user: {
        id: user.id,
        email: user.email,
        name: user.name
      }
    });
  } catch (error) {
    console.error('Auth error:', error);
    res.status(error.statusCode || 500).json({ error: error.statusCode ? error.message : 'Authentication failed' });
  }
});

// Verify token
router.post('/verify', (req, res) => {
  try {
    const token = req.headers.authorization?.split(' ')[1];

    if (!token) {
      return res.status(401).json({ error: 'No token' });
    }

    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    res.json({ valid: true, userId: decoded.userId });
  } catch (error) {
    res.status(403).json({ error: 'Invalid token' });
  }
});

module.exports = router;
