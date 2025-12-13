import React, { useState, useEffect, useCallback, useRef } from 'react';
import { FixedSizeList as List } from 'react-window';
import InfiniteLoader from 'react-window-infinite-loader';
import axios from 'axios';
import { Box, Typography, CircularProgress, Tooltip } from '@mui/material';
import { useQueryClient } from '@tanstack/react-query';
import ConfidenceBar from '../Reconcile/ConfidenceBar'; // Defined in Docs/06 Section 6

/**
 * VirtualGrid Component
 * =====================
 * Implements the "Virtual Scroll" strategy defined in Docs/06_API_AND_FRONTEND.md.
 * * Logic:
 * 1. Renders a viewport that only contains ~20 DOM nodes.
 * 2. As the user scrolls, 'InfiniteLoader' calculates which indices are visible.
 * 3. Triggers 'loadMoreItems' to fetch specific row batches from Java Core (Layer 3).
 * 4. Hydrates cells with 'SmartCell' data, including the 'ConfidenceBar' for matched entities.
 */

// Constants
const ROW_HEIGHT = 50;
const BATCH_SIZE = 50; // Fetch 50 rows at a time (matches backend page size)

const VirtualGrid = ({ projectId, engineConfig }) => {
  const [rowCount, setRowCount] = useState(0); // Total rows in project
  const [isNextPageLoading, setIsNextPageLoading] = useState(false);
  const [rows, setRows] = useState({}); // Map of index -> Row Data
  
  // Ref to track request cancellation if user scrolls fast
  const abortController = useRef(null);

  // ===========================================================================
  // 1. Initial Data Load
  // ===========================================================================
  useEffect(() => {
    // Reset state when project or filter (engine) changes
    setRows({});
    setRowCount(0);
    loadMoreItems(0, BATCH_SIZE);
  }, [projectId, engineConfig]);

  // ===========================================================================
  // 2. Data Fetching Logic (Layer 3 Link)
  // ===========================================================================
  const loadMoreItems = async (startIndex, stopIndex) => {
    if (isNextPageLoading) return;
    setIsNextPageLoading(true);

    // Cancel previous pending requests to prevent race conditions
    if (abortController.current) {
      abortController.current.abort();
    }
    abortController.current = new AbortController();

    try {
      const limit = stopIndex - startIndex + 1;
      
      // API Call: Docs/06 Section 4.1
      const response = await axios.post(
        '/command/core/get-rows', 
        new URLSearchParams({
          project: projectId,
          start: startIndex,
          limit: limit,
          engine: JSON.stringify(engineConfig) // Serialize current facets
        }),
        { 
          signal: abortController.current.signal,
          headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }
        }
      );

      const data = response.data;

      if (data.code === 'ok') {
        setRowCount(data.total); // Update total scrollable height
        
        // Merge new rows into state
        setRows(prev => {
          const next = { ...prev };
          data.rows.forEach(row => {
            next[row.i] = row; // Map by absolute row index
          });
          return next;
        });
      }
    } catch (error) {
      if (!axios.isCancel(error)) {
        console.error("Grid Fetch Error:", error);
      }
    } finally {
      setIsNextPageLoading(false);
    }
  };

  // ===========================================================================
  // 3. Render Helpers
  // ===========================================================================
  
  // Predicate: Is this row index already loaded in memory?
  const isItemLoaded = index => !!rows[index];

  // The Row Renderer (Memoized by react-window)
  const Row = ({ index, style }) => {
    const rowData = rows[index];

    // Case A: Skeleton Loading
    if (!rowData) {
      return (
        <div style={style} className="grid-row-loading">
          <Box sx={{ display: 'flex', alignItems: 'center', height: '100%', px: 2 }}>
            <CircularProgress size={16} sx={{ mr: 2 }} />
            <Typography variant="body2" color="text.secondary">Loading Row {index}...</Typography>
          </Box>
        </div>
      );
    }

    // Case B: Render SmartCell
    // For simplicity in this view, we assume a single reconciled column logic.
    // In production, this would map across rowData.cells.
    const primaryCell = rowData.cells[0]; // Assuming first column is the target
    
    return (
      <div style={style} className={`grid-row ${index % 2 === 0 ? 'even' : 'odd'}`}>
        <Box sx={{ 
          display: 'flex', 
          alignItems: 'center', 
          height: '100%', 
          px: 2, 
          borderBottom: '1px solid #eee',
          backgroundColor: index % 2 === 0 ? '#fafafa' : '#fff' 
        }}>
          
          {/* Column 1: Row Index */}
          <Typography variant="caption" sx={{ width: 40, color: '#999' }}>
            {index + 1}
          </Typography>

          {/* Column 2: Raw Value */}
          <Typography variant="body2" sx={{ width: 200, fontWeight: 500 }}>
            {primaryCell.v}
          </Typography>

          {/* Column 3: Reconciliation Status / Confidence Bar */}
          <Box sx={{ flex: 1, ml: 2 }}>
            <ReconStatus cell={primaryCell} />
          </Box>
        </Box>
      </div>
    );
  };

  return (
    <Box sx={{ height: '100%', width: '100%', border: '1px solid #ddd' }}>
      <InfiniteLoader
        isItemLoaded={isItemLoaded}
        itemCount={rowCount}
        loadMoreItems={loadMoreItems}
        minimumBatchSize={BATCH_SIZE}
        threshold={10} // Start fetching when 10 rows away from bottom
      >
        {({ onItemsRendered, ref }) => (
          <List
            className="virtual-grid"
            height={600} // Viewport Height
            itemCount={rowCount}
            itemSize={ROW_HEIGHT}
            onItemsRendered={onItemsRendered}
            ref={ref}
            width={'100%'}
          >
            {Row}
          </List>
        )}
      </InfiniteLoader>
    </Box>
  );
};

// =============================================================================
// Sub-Component: ReconStatus
// Handles the polymorphic display of the SmartCell state
// =============================================================================
const ReconStatus = ({ cell }) => {
  if (!cell.recon) {
    return <Typography variant="caption" color="text.disabled">New</Typography>;
  }

  // Case 1: Matched (Show Confidence Bar)
  if (cell.recon.judgment === 'matched' && cell.recon.match) {
    return (
      <Box>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 0.5 }}>
          <Typography variant="body2" sx={{ fontWeight: 'bold', mr: 1, color: '#2e7d32' }}>
            {cell.recon.match.name}
          </Typography>
          <Typography variant="caption" sx={{ color: '#2e7d32', border: '1px solid #2e7d32', borderRadius: 1, px: 0.5 }}>
            {cell.recon.match.id}
          </Typography>
        </Box>
        {/* The Visualization Component defined in Docs/06 Section 6 */}
        <ConfidenceBar features={cell.recon.features || cell.recon.match.features} />
      </Box>
    );
  }

  // Case 2: Ambiguous (Show Candidates)
  if (cell.recon.candidates && cell.recon.candidates.length > 0) {
    return (
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Typography variant="body2" color="warning.main">Ambiguous:</Typography>
        {cell.recon.candidates.slice(0, 2).map(c => (
          <Tooltip key={c.id} title={`Score: ${c.score.toFixed(1)}`}>
             <Typography variant="body2" sx={{ textDecoration: 'underline', cursor: 'pointer' }}>
               {c.name}
             </Typography>
          </Tooltip>
        ))}
      </Box>
    );
  }

  return <Typography variant="caption" color="text.secondary">Processing...</Typography>;
};

export default VirtualGrid;