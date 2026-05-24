import React from 'react';

export const FlowGraphContext = React.createContext<{ isReadonly: boolean }>({ isReadonly: false });
