package home

import "github.com/router-for-me/CLIProxyAPI/v8/sdk/pluginapi"

// SchedulerContinuityError preserves the structured Home error envelope so the
// execution layer can apply the normal dispatch error classification.
type SchedulerContinuityError struct {
	Payload []byte
}

func (e *SchedulerContinuityError) Error() string {
	return "home rejected scheduler continuity observation"
}

type SchedulerContinuityQuery struct {
	Request                   pluginapi.SchedulerContinuityRequest `json:"request"`
	CredentialPolicy          string                               `json:"credential_policy,omitempty"`
	RetryRound                int                                  `json:"retry_round"`
	ApplyRetryRoundExclusions bool                                 `json:"apply_retry_round_exclusions"`
	ExcludedAuthIDs           []string                             `json:"excluded_auth_ids,omitempty"`
	PinnedAuthID              string                               `json:"pinned_auth_id,omitempty"`
}
