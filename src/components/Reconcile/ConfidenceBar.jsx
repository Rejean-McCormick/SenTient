import React from 'react';
import { Box, Tooltip, Typography } from '@mui/material';
import StarIcon from '@mui/icons-material/Star';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';

/**
 * ConfidenceBar Component
 * =======================
 * Visualizes the "SenTient Consensus Score" as a stacked bar chart.
 * Logic defined in Docs/06_API_AND_FRONTEND.md:
 * - Total Width represents the Final Score (0-100).
 * - Segments represent the contribution of each layer:
 * 1. Blue:   Solr Popularity (Max 40% contribution)
 * 2. Green:  Falcon Context  (Max 30% contribution)
 * 3. Yellow: Levenshtein     (Max 30% contribution)
 */

const ConfidenceBar = ({ features }) => {
  // Defensive check for missing telemetry (e.g., pre-computation state)
  if (!features) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', color: 'text.disabled' }}>
        <Typography variant="caption">No Score Data</Typography>
        <HelpOutlineIcon fontSize="small" sx={{ ml: 0.5, fontSize: 14 }} />
      </Box>
    );
  }

  // Destructure features (Values are normalized 0.0 - 1.0 from Java Core)
  const popularity = features.tapioca_popularity || 0;
  const context = features.falcon_context || 0;
  const spelling = features.levenshtein_distance || 0;

  // Calculate segment widths based on weights defined in 'environment.json'
  // The bar represents the score percentage (0-100%)
  const widthPop = popularity * 40;   // Max 40%
  const widthCtx = context * 30;      // Max 30%
  const widthSpell = spelling * 30;   // Max 30%
  
  const totalScore = Math.round(widthPop + widthCtx + widthSpell);

  // High Context Indicator
  // "If falcon_context > 0.8, add a 'Star' icon."
  const isContextStar = context > 0.8;

  return (
    <Box sx={{ width: '100%', minWidth: 120 }}>
      {/* 1. Header: Total Score & Context Badge */}
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 0.5, justifyContent: 'space-between' }}>
        <Typography variant="caption" sx={{ fontWeight: 'bold', color: '#333' }}>
          {totalScore}% Confidence
        </Typography>
        
        {isContextStar && (
          <Tooltip title="High Context Match (Falcon)">
            <StarIcon sx={{ color: '#ffc107', fontSize: 16 }} />
          </Tooltip>
        )}
      </Box>

      {/* 2. Stacked Bar Chart */}
      <Box sx={{ 
        display: 'flex', 
        height: 8, 
        width: '100%', 
        bgcolor: '#e0e0e0', 
        borderRadius: 1,
        overflow: 'hidden' 
      }}>
        
        {/* Segment A: Popularity (Layer 1) */}
        <Tooltip title={`Popularity (Solr): ${(popularity * 100).toFixed(0)}%`}>
          <Box sx={{ 
            width: `${widthPop}%`, 
            bgcolor: '#2196f3', // Blue
            transition: 'width 0.5s ease' 
          }} />
        </Tooltip>

        {/* Segment B: Context (Layer 2) */}
        <Tooltip title={`Context (Falcon): ${(context * 100).toFixed(0)}%`}>
          <Box sx={{ 
            width: `${widthCtx}%`, 
            bgcolor: '#4caf50', // Green
            transition: 'width 0.5s ease' 
          }} />
        </Tooltip>

        {/* Segment C: Spelling (Layer 3) */}
        <Tooltip title={`Spelling (Levenshtein): ${(spelling * 100).toFixed(0)}%`}>
          <Box sx={{ 
            width: `${widthSpell}%`, 
            bgcolor: '#ffeb3b', // Yellow
            transition: 'width 0.5s ease' 
          }} />
        </Tooltip>

      </Box>
    </Box>
  );
};

export default ConfidenceBar;